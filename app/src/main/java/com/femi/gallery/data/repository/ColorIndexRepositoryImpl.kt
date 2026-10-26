package com.femi.gallery.data.repository

import android.content.Context
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.femi.gallery.core.permission.MediaPermissionChecker
import com.femi.gallery.data.color.ColorIndexHolder
import com.femi.gallery.data.color.ColorScanner
import com.femi.gallery.data.mediastore.MediaStoreDataSource
import com.femi.gallery.di.ApplicationScope
import com.femi.gallery.domain.color.ColorScanState
import com.femi.gallery.domain.color.ColorScanStatus
import com.femi.gallery.domain.color.DetectedColor
import com.femi.gallery.domain.repository.ColorIndexRepository
import com.femi.gallery.domain.repository.ColorStore
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
class ColorIndexRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataSource: MediaStoreDataSource,
    private val scanner: ColorScanner,
    private val store: ColorStore,
    private val index: ColorIndexHolder,
    private val permissions: MediaPermissionChecker,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ColorIndexRepository {

    private val _scan = MutableStateFlow(ColorScanState())
    override val scan: StateFlow<ColorScanState> = _scan.asStateFlow()

    private val lock = Any()
    private var job: Job? = null
    private val workers = Dispatchers.Default.limitedParallelism(3)

    @Volatile
    private var completedGeneration = -1L

    private sealed interface Outcome {
        class Read(val entry: DetectedColor) : Outcome
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
            _scan.update { it.copy(status = ColorScanStatus.Idle) }
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

            val results = ArrayList<DetectedColor>(kept)
            var withColor = kept.count { it.colors.isNotEmpty() }
            var scanned = 0
            var unsaved = 0
            _scan.value = ColorScanState(ColorScanStatus.Scanning, 0, plan.toScan, withColor)

            var next = 0
            while (next < plan.toScan) {
                val end = minOf(plan.toScan, next + BATCH_SIZE)
                val outcomes = coroutineScope {
                    (next until end)
                        .map { i ->
                            async(workers) {
                                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                                val colors = scanner.scan(plan.scanIds[i])
                                Outcome.Read(DetectedColor(plan.scanIds[i], plan.scanModified[i], colors.joinToString(" ")))
                            }
                        }
                        .awaitAll()
                }
                val read = outcomes.map { it.entry }
                results.addAll(read)
                withColor += read.count { it.colors.isNotEmpty() }
                scanned += outcomes.size
                unsaved += read.size
                if (unsaved >= CHECKPOINT_EVERY) {
                    checkpoint(results)
                    unsaved = 0
                }
                _scan.value = ColorScanState(ColorScanStatus.Scanning, scanned, plan.toScan, withColor)
                next = end
            }
            if (unsaved > 0) checkpoint(results)

            completedGeneration = generation
            _scan.value = ColorScanState(ColorScanStatus.Idle, scanned, plan.toScan, withColor)
            Log.i(TAG, "Scanned colours for ${plan.toScan} photos in ${SystemClock.elapsedRealtime() - startedAt} ms")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Color scan failed", e)
            _scan.update { it.copy(status = ColorScanStatus.Idle) }
        }
    }

    private suspend fun checkpoint(entries: List<DetectedColor>) {
        val snapshot = entries.toList()
        index.publish(snapshot)
        store.save(snapshot)
    }

    private companion object {
        const val TAG = "ColorScan"
        const val BATCH_SIZE = 32
        const val CHECKPOINT_EVERY = 100
    }
}
