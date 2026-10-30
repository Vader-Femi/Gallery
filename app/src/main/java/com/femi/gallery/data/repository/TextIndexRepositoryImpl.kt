package com.femi.gallery.data.repository

import android.content.Context
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.femi.gallery.core.permission.MediaPermissionChecker
import com.femi.gallery.data.mediastore.MediaStoreDataSource
import com.femi.gallery.data.text.TextIndexHolder
import com.femi.gallery.di.ApplicationScope
import com.femi.gallery.domain.repository.TextIndexRepository
import com.femi.gallery.domain.repository.TextRecognizer
import com.femi.gallery.domain.repository.TextStore
import com.femi.gallery.domain.text.DetectedText
import com.femi.gallery.domain.text.StampPlanner
import com.femi.gallery.domain.text.TextScanState
import com.femi.gallery.domain.text.TextScanStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the text in every photo, one batch at a time, and keeps what it finds so that search can
 * match words written in photos. It is resumable (a checkpoint is saved every [CHECKPOINT_EVERY]
 * photos) and incremental (only new and changed photos are read), like the location scan. The first
 * run over a large library takes a while: the Logcat tag "TextScan" shows how it is going.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TextIndexRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataSource: MediaStoreDataSource,
    private val recognizer: TextRecognizer,
    private val store: TextStore,
    private val index: TextIndexHolder,
    private val permissions: MediaPermissionChecker,
    @param:ApplicationScope private val scope: CoroutineScope,
) : TextIndexRepository {

    private val _scan = MutableStateFlow(TextScanState())
    override val scan: StateFlow<TextScanState> = _scan.asStateFlow()

    private val lock = Any()
    private var job: Job? = null

    /** Two photos are read at a time: enough to keep the recogniser busy, not enough to slow the app down. */
    private val workers = Dispatchers.Default.limitedParallelism(PARALLEL_READS)

    /** The MediaStore generation of the last scan that ran to the end; nothing to do while it holds. */
    @Volatile
    private var completedGeneration = -1L

    /** What reading one photo came to. */
    private sealed interface Outcome {
        class Read(val entry: DetectedText) : Outcome
        data object Gone : Outcome
        data object Failed : Outcome
    }

    override val isRunning: Boolean get() = synchronized(lock) { job?.isActive == true }

    override fun stopScan() {
        synchronized(lock) {
            job?.cancel()
            job = null
        }
        _scan.update { it.copy(status = TextScanStatus.Idle) }
    }

    override fun startScan() {
        if (!permissions.hasMediaAccess()) {
            Log.i(TAG, "Not scanning: no access to photos")
            return
        }
        if (context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true) {
            Log.i(TAG, "Not scanning: battery saver is on")
            return
        }
        synchronized(lock) {
            if (job?.isActive == true) return
            Log.i(TAG, "Starting scan")
            job = scope.launch { runScan() }
        }
    }

    private suspend fun runScan() {
        val generation = dataSource.generation()
        if (generation == completedGeneration) {
            Log.i(TAG, "Nothing new since the last scan")
            _scan.update { it.copy(status = TextScanStatus.Idle) }
            return
        }
        val startedAt = SystemClock.elapsedRealtime()
        val results = ArrayList<DetectedText>()
        var unsaved = 0
        try {
            val known = index.current().entries
            val stamps = dataSource.loadImageStamps()
            val plan = StampPlanner.plan(
                cachedIds = LongArray(known.size) { known[it].id },
                cachedModified = LongArray(known.size) { known[it].modified },
                ids = stamps.ids,
                modified = stamps.modified,
            )
            val kept = known.filter { it.id in plan.keepIds }
            if (kept.size != known.size) checkpoint(kept) // forget deleted and changed photos

            results.addAll(kept)
            var withText = kept.count { it.text.isNotEmpty() }
            var scanned = 0
            var failedBatches = 0
            Log.i(TAG, "${stamps.size} photos, ${kept.size} already read, ${plan.toScan} to read")
            _scan.value = TextScanState(TextScanStatus.Scanning, 0, plan.toScan, withText)

            var next = 0
            while (next < plan.toScan) {
                val end = minOf(plan.toScan, next + BATCH_SIZE)
                val outcomes = coroutineScope {
                    (next until end)
                        .map { i -> async(workers) { readOne(plan.scanIds[i], plan.scanModified[i]) } }
                        .awaitAll()
                }
                val read = outcomes.filterIsInstance<Outcome.Read>().map { it.entry }
                if (read.isEmpty() && outcomes.any { it is Outcome.Failed }) {
                    // Nothing in the whole batch could be read, and not because the photos were gone:
                    // reading text does not work (on this phone, or now). Do not remember it as "no text".
                    if (++failedBatches >= MAX_FAILED_BATCHES) {
                        Log.w(TAG, "Reading text keeps failing; giving up")
                        _scan.value = TextScanState(TextScanStatus.Unavailable, scanned, plan.toScan, withText)
                        return
                    }
                } else {
                    failedBatches = 0
                }
                results.addAll(read)
                withText += read.count { it.text.isNotEmpty() }
                scanned += outcomes.size
                unsaved += read.size
                if (unsaved >= CHECKPOINT_EVERY) {
                    checkpoint(results)
                    unsaved = 0
                    Log.i(TAG, "Saved: $scanned of ${plan.toScan} read, $withText with text")
                }
                _scan.value = TextScanState(TextScanStatus.Scanning, scanned, plan.toScan, withText)
                next = end
            }
            if (unsaved > 0) checkpoint(results)

            completedGeneration = generation
            _scan.value = TextScanState(TextScanStatus.Idle, scanned, plan.toScan, withText)
            Log.i(
                TAG,
                "Read ${plan.toScan} photos in ${SystemClock.elapsedRealtime() - startedAt} ms; " +
                    "$withText of ${results.size} contain text",
            )
        } catch (e: CancellationException) {
            if (unsaved > 0) {
                withContext(NonCancellable) { checkpoint(results) }
            }
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Text scan failed", e)
            _scan.update { it.copy(status = TextScanStatus.Idle) }
        }
    }

    private suspend fun readOne(id: Long, modified: Long): Outcome = withContext(workers) {
        // Behind the user's own use of the app.
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        try {
            recognizer.recognize(id)?.let { Outcome.Read(DetectedText(id, modified, it)) } ?: Outcome.Gone
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the text of photo $id", e)
            Outcome.Failed
        }
    }

    /** Makes [entries] what searches use and what is kept for the next run. */
    private suspend fun checkpoint(entries: List<DetectedText>) {
        val snapshot = entries.toList()
        index.publish(snapshot)
        store.save(snapshot)
    }

    private companion object {
        const val TAG = "TextScan"
        const val PARALLEL_READS = 2
        const val BATCH_SIZE = 16
        const val CHECKPOINT_EVERY = 100
        const val MAX_FAILED_BATCHES = 3
    }
}
