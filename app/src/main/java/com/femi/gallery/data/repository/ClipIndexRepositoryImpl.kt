package com.femi.gallery.data.repository

import android.content.Context
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.femi.gallery.core.permission.MediaPermissionChecker
import com.femi.gallery.data.clip.ClipIndexHolder
import com.femi.gallery.data.mediastore.MediaStoreDataSource
import com.femi.gallery.di.ApplicationScope
import com.femi.gallery.domain.clip.ClipScanState
import com.femi.gallery.domain.clip.ClipScanStatus
import com.femi.gallery.domain.clip.DetectedEmbedding
import com.femi.gallery.domain.repository.ClipIndexRepository
import com.femi.gallery.domain.repository.ClipStore
import com.femi.gallery.domain.repository.ImageEmbedder
import com.femi.gallery.domain.text.StampPlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipIndexRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataSource: MediaStoreDataSource,
    private val embedder: ImageEmbedder,
    private val store: ClipStore,
    private val index: ClipIndexHolder,
    private val permissions: MediaPermissionChecker,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ClipIndexRepository {

    private val _scan = MutableStateFlow(ClipScanState())
    override val scan: StateFlow<ClipScanState> = _scan.asStateFlow()

    private val lock = Any()
    private var job: Job? = null
    private val workers = Dispatchers.Default.limitedParallelism(2)

    @Volatile
    private var completedGeneration = -1L

    private sealed interface Outcome {
        class Read(val entry: DetectedEmbedding) : Outcome
    }

    override fun startScan() {
        if (!embedder.isAvailable) return
        if (!permissions.hasMediaAccess()) return
        if (context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true) return
        synchronized(lock) {
            if (job?.isActive == true) return
            job = scope.launch { runScan() }
        }
    }

    private suspend fun runScan() {
        val generation = dataSource.generation()
        if (generation == completedGeneration) {
            _scan.update { it.copy(status = ClipScanStatus.Idle) }
            return
        }
        val startedAt = SystemClock.elapsedRealtime()
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
            if (kept.size != known.size) checkpoint(kept)

            val results = ArrayList<DetectedEmbedding>(kept)
            var withEmbeddings = kept.count { it.embedding.isNotEmpty() }
            var scanned = 0
            var unsaved = 0
            _scan.value = ClipScanState(ClipScanStatus.Scanning, 0, plan.toScan, withEmbeddings)

            var next = 0
            while (next < plan.toScan) {
                val end = minOf(plan.toScan, next + BATCH_SIZE)
                val outcomes = coroutineScope {
                    (next until end)
                        .map { i ->
                            async(workers) {
                                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                                val emb = embedder.embed(plan.scanIds[i]) ?: FloatArray(0)
                                Outcome.Read(DetectedEmbedding(plan.scanIds[i], plan.scanModified[i], emb))
                            }
                        }
                        .awaitAll()
                }
                val read = outcomes.map { it.entry }
                results.addAll(read)
                withEmbeddings += read.count { it.embedding.isNotEmpty() }
                scanned += outcomes.size
                unsaved += read.size
                if (unsaved >= CHECKPOINT_EVERY) {
                    checkpoint(results)
                    unsaved = 0
                }
                _scan.value = ClipScanState(ClipScanStatus.Scanning, scanned, plan.toScan, withEmbeddings)
                next = end
            }
            if (unsaved > 0) checkpoint(results)

            completedGeneration = generation
            _scan.value = ClipScanState(ClipScanStatus.Idle, scanned, plan.toScan, withEmbeddings)
            Log.i(TAG, "Scanned embeddings for ${plan.toScan} photos in ${SystemClock.elapsedRealtime() - startedAt} ms")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Clip scan failed", e)
            _scan.update { it.copy(status = ClipScanStatus.Idle) }
        }
    }

    private suspend fun checkpoint(entries: List<DetectedEmbedding>) {
        val snapshot = entries.toList()
        index.publish(snapshot)
        store.save(snapshot)
    }

    private companion object {
        const val TAG = "ClipScan"
        const val BATCH_SIZE = 16
        const val CHECKPOINT_EVERY = 100
    }
}
