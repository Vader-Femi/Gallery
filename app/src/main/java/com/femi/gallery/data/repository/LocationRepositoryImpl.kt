package com.femi.gallery.data.repository

import android.Manifest
import android.os.SystemClock
import android.util.Log
import com.femi.gallery.core.permission.MediaPermissionChecker
import com.femi.gallery.data.location.LocationIndex
import com.femi.gallery.data.location.LocationPermissionException
import com.femi.gallery.data.location.LocationScanner
import com.femi.gallery.data.mediastore.MediaStoreDataSource
import com.femi.gallery.di.ApplicationScope
import com.femi.gallery.domain.geo.LocatedMedia
import com.femi.gallery.domain.geo.LocationEntry
import com.femi.gallery.domain.geo.LocationScanPlanner
import com.femi.gallery.domain.geo.LocationScanState
import com.femi.gallery.domain.geo.LocationScanStatus
import com.femi.gallery.domain.repository.LocationRepository
import com.femi.gallery.domain.repository.LocationStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val dataSource: MediaStoreDataSource,
    private val scanner: LocationScanner,
    private val store: LocationStore,
    private val index: LocationIndex,
    private val permissions: MediaPermissionChecker,
    @param:ApplicationScope private val scope: CoroutineScope,
) : LocationRepository {

    private val _scan = MutableStateFlow(LocationScanState())
    override val scan: StateFlow<LocationScanState> = _scan.asStateFlow()

    private val lock = Any()
    private var job: Job? = null

    /** The MediaStore generation of the last scan that ran to the end; nothing to do while it holds. */
    @Volatile
    private var completedGeneration = -1L

    override val isRunning: Boolean get() = synchronized(lock) { job?.isActive == true }

    override fun stopScan() {
        synchronized(lock) {
            job?.cancel()
            job = null
        }
        _scan.update { it.copy(status = LocationScanStatus.Idle) }
    }

    override fun startScan() {
        if (!permissions.hasMediaAccess()) {
            Log.i(TAG, "Not scanning: no access to photos")
            return
        }
        if (!permissions.isGranted(Manifest.permission.ACCESS_MEDIA_LOCATION)) {
            Log.i(TAG, "Not scanning: ACCESS_MEDIA_LOCATION is not granted")
            _scan.update { it.copy(status = LocationScanStatus.NeedsPermission) }
            return
        }
        synchronized(lock) {
            if (job?.isActive == true) {
                Log.d(TAG, "Scan already running")
                return
            }
            Log.i(TAG, "Starting scan")
            job = scope.launch { runScan() }
        }
    }

    override suspend fun locatedMedia(): LocatedMedia =
        LocatedMedia.from(dataSource.loadOrder(), index.current())

    private suspend fun runScan() {
        val generation = dataSource.generation()
        if (generation == completedGeneration) {
            Log.i(TAG, "Nothing new since the last scan")
            _scan.update { it.copy(status = LocationScanStatus.Idle) }
            return
        }
        val startedAt = SystemClock.elapsedRealtime()
        try {
            val known = index.current().entries
            val stamps = dataSource.loadImageStamps()
            val plan = LocationScanPlanner.plan(known, stamps.ids, stamps.modified)
            if (plan.keep.size != known.size) checkpoint(plan.keep) // forget deleted and changed photos

            val results = ArrayList<LocationEntry>(plan.keep)
            var placed = plan.keep.count { it.place != null }
            var scanned = 0
            var unsaved = 0
            Log.i(TAG, "${stamps.size} photos, ${plan.keep.size} already known, ${plan.toScan} to read")
            _scan.value = LocationScanState(LocationScanStatus.Scanning, 0, plan.toScan, placed)

            scanner.scan(plan.scanIds, plan.scanModified) { entries, covered ->
                results.addAll(entries)
                placed += entries.count { it.place != null }
                scanned += covered
                unsaved += entries.size
                if (unsaved >= CHECKPOINT_EVERY) {
                    checkpoint(results)
                    unsaved = 0
                    Log.i(TAG, "Saved: $scanned of ${plan.toScan} read, $placed with a place")
                }
                _scan.value = LocationScanState(LocationScanStatus.Scanning, scanned, plan.toScan, placed)
            }
            if (unsaved > 0) checkpoint(results)

            completedGeneration = generation
            _scan.value = LocationScanState(LocationScanStatus.Idle, scanned, plan.toScan, placed)
            Log.i(
                TAG,
                "Read ${plan.toScan} photos in ${SystemClock.elapsedRealtime() - startedAt} ms; " +
                    "$placed of ${results.size} have a place",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocationPermissionException) {
            Log.w(TAG, "Location in photos is not allowed; scan stopped", e)
            _scan.update { it.copy(status = LocationScanStatus.NeedsPermission) }
        } catch (e: Exception) {
            Log.w(TAG, "Location scan failed", e)
            _scan.update { it.copy(status = LocationScanStatus.Idle) }
        }
    }

    /** Makes [entries] what the app searches by and what is kept for the next run. */
    private suspend fun checkpoint(entries: List<LocationEntry>) {
        val snapshot = entries.toList()
        index.publish(snapshot)
        store.save(snapshot)
    }

    private companion object {
        const val TAG = "LocationScan"
        const val CHECKPOINT_EVERY = 1_000
    }
}
