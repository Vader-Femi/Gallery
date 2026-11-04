package com.femi.gallery.data.repository

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.femi.gallery.core.permission.MediaPermissionChecker
import com.femi.gallery.data.db.FaceDao
import com.femi.gallery.data.db.FaceScanDao
import com.femi.gallery.data.db.FaceScanRow
import com.femi.gallery.data.mediastore.MediaStoreDataSource
import com.femi.gallery.di.ApplicationScope
import com.femi.gallery.domain.face.FaceClusterer
import com.femi.gallery.domain.face.FaceScanState
import com.femi.gallery.domain.face.FaceScanStatus
import com.femi.gallery.domain.model.MediaType
import com.femi.gallery.domain.search.SearchCriteria
import com.femi.gallery.domain.repository.FaceDetector
import com.femi.gallery.domain.repository.FaceIndexRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FaceIndexRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataSource: MediaStoreDataSource,
    private val detector: FaceDetector,
    private val clusterer: FaceClusterer,
    private val scanDao: FaceScanDao,
    private val faceDao: FaceDao,
    private val permissions: MediaPermissionChecker,
    @param:ApplicationScope private val scope: CoroutineScope,
) : FaceIndexRepository {

    private val _scan = MutableStateFlow(FaceScanState())
    override val scan: StateFlow<FaceScanState> = _scan.asStateFlow()

    private val lock = Any()
    private var job: Job? = null

    override val isRunning: Boolean get() = synchronized(lock) { job?.isActive == true }

    override fun stopScan() {
        synchronized(lock) {
            job?.cancel()
            job = null
        }
        _scan.update { it.copy(status = FaceScanStatus.Idle) }
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
        _scan.update { it.copy(status = FaceScanStatus.Scanning) }
        try {
            val order = dataSource.loadOrder(search = SearchCriteria(mediaType = MediaType.Image))
            if (order.size == 0) {
                _scan.update { it.copy(status = FaceScanStatus.Idle, scanned = 0, total = 0) }
                return
            }

            val existingScans = scanDao.all().associateBy { it.id }
            val mediaIds = order.ids
            val dates = order.datesMillis

            val toScan = mutableListOf<Long>()
            for (i in 0 until order.size) {
                val id = mediaIds[i]
                val date = dates[i]
                val existing = existingScans[id]
                if (existing == null || existing.modified < date) {
                    toScan.add(id)
                }
            }

            // Remove scans for photos that no longer exist
            val presentIds = mediaIds.toSet()
            val removedIds = existingScans.keys.filter { it !in presentIds }
            if (removedIds.isNotEmpty()) {
                scanDao.delete(removedIds)
                faceDao.deleteForMedia(removedIds)
            }

            val total = toScan.size
            var scannedCount = 0
            _scan.update { it.copy(scanned = 0, total = total, status = FaceScanStatus.Scanning) }

            val semaphore = Semaphore(2)
            val batchSize = 16

            for (chunk in toScan.chunked(batchSize)) {
                val detectedBatch = chunk.map { id ->
                    scope.async(Dispatchers.Default) {
                        semaphore.withPermit {
                            id to detector.detectFaces(id)
                        }
                    }
                }.awaitAll()

                val results = mutableListOf<FaceScanRow>()
                for ((id, faces) in detectedBatch) {
                    val photoPeople = mutableSetOf<Long>()
                    for (face in faces) {
                        val boundsStr = "${face.bounds.left},${face.bounds.top},${face.bounds.right},${face.bounds.bottom}"
                        val personId = clusterer.assignFace(id, boundsStr, face.feature, excludedPersonIds = photoPeople)
                        photoPeople.add(personId)
                    }
                    results.add(FaceScanRow(id = id, modified = System.currentTimeMillis(), faceCount = faces.size))
                }

                scanDao.upsert(results)
                scannedCount += results.size
                _scan.update { it.copy(scanned = scannedCount, total = total) }
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Face scan cancelled")
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Face scan error", e)
        } finally {
            _scan.update { it.copy(status = FaceScanStatus.Idle) }
            synchronized(lock) { job = null }
        }
    }

    private companion object {
        const val TAG = "FaceIndexRepo"
    }
}
