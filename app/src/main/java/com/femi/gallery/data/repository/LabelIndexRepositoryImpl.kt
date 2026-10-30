package com.femi.gallery.data.repository

import android.content.Context
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.femi.gallery.core.permission.MediaPermissionChecker
import com.femi.gallery.data.label.LabelIndexHolder
import com.femi.gallery.data.mediastore.MediaStoreDataSource
import com.femi.gallery.di.ApplicationScope
import com.femi.gallery.domain.label.DetectedLabel
import com.femi.gallery.domain.label.LabelScanState
import com.femi.gallery.domain.label.LabelScanStatus
import com.femi.gallery.domain.repository.ImageLabeller
import com.femi.gallery.domain.repository.LabelIndexRepository
import com.femi.gallery.domain.repository.LabelStore
import com.femi.gallery.domain.text.StampPlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

@Singleton
class LabelIndexRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataSource: MediaStoreDataSource,
    private val labeller: ImageLabeller,
    private val store: LabelStore,
    private val index: LabelIndexHolder,
    private val permissions: MediaPermissionChecker,
    @param:ApplicationScope private val scope: CoroutineScope,
) : LabelIndexRepository {

    private val _scan = MutableStateFlow(LabelScanState())
    override val scan: StateFlow<LabelScanState> = _scan.asStateFlow()

    private val lock = Any()
    private var job: Job? = null
    private val workers = Dispatchers.Default.limitedParallelism(2)

    @Volatile
    private var completedGeneration = -1L

    private sealed interface Outcome {
        class Read(val entry: DetectedLabel) : Outcome
    }

    override val isRunning: Boolean get() = synchronized(lock) { job?.isActive == true }

    override fun stopScan() {
        synchronized(lock) {
            job?.cancel()
            job = null
        }
        _scan.update { it.copy(status = LabelScanStatus.Idle) }
    }

    override fun startScan() {
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
            _scan.update { it.copy(status = LabelScanStatus.Idle) }
            return
        }
        val startedAt = SystemClock.elapsedRealtime()
        val results = ArrayList<DetectedLabel>()
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
            if (kept.size != known.size) checkpoint(kept)

            results.addAll(kept)
            var withLabels = kept.count { it.labels.isNotEmpty() }
            var scanned = 0
            _scan.value = LabelScanState(LabelScanStatus.Scanning, 0, plan.toScan, withLabels)

            var next = 0
            while (next < plan.toScan) {
                val end = minOf(plan.toScan, next + BATCH_SIZE)
                val outcomes = coroutineScope {
                    (next until end)
                        .map { i ->
                            async(workers) {
                                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                                val labels = labeller.label(plan.scanIds[i])
                                Outcome.Read(DetectedLabel(plan.scanIds[i], plan.scanModified[i], labels.joinToString(" ")))
                            }
                        }
                        .awaitAll()
                }
                val read = outcomes.map { it.entry }
                results.addAll(read)
                withLabels += read.count { it.labels.isNotEmpty() }
                scanned += outcomes.size
                unsaved += read.size
                if (unsaved >= CHECKPOINT_EVERY) {
                    checkpoint(results)
                    unsaved = 0
                }
                _scan.value = LabelScanState(LabelScanStatus.Scanning, scanned, plan.toScan, withLabels)
                next = end
            }
            if (unsaved > 0) checkpoint(results)

            completedGeneration = generation
            _scan.value = LabelScanState(LabelScanStatus.Idle, scanned, plan.toScan, withLabels)
            Log.i(TAG, "Scanned labels for ${plan.toScan} photos in ${SystemClock.elapsedRealtime() - startedAt} ms")
        } catch (e: CancellationException) {
            if (unsaved > 0) {
                withContext(NonCancellable) { checkpoint(results) }
            }
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Label scan failed", e)
            _scan.update { it.copy(status = LabelScanStatus.Idle) }
        }
    }

    private suspend fun checkpoint(entries: List<DetectedLabel>) {
        val snapshot = entries.toList()
        index.publish(snapshot)
        store.save(snapshot)
    }

    private companion object {
        const val TAG = "LabelScan"
        const val BATCH_SIZE = 16
        const val CHECKPOINT_EVERY = 100
    }
}
