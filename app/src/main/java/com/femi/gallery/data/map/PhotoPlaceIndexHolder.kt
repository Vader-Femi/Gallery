package com.femi.gallery.data.map

import com.femi.gallery.data.location.LocationIndex
import com.femi.gallery.domain.geo.PhotoPlaceIndex
import com.femi.gallery.domain.repository.MapDataRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoPlaceIndexHolder @Inject constructor(
    private val locationIndex: LocationIndex,
    private val mapDataRepository: MapDataRepository,
) {
    private val mutex = Mutex()

    @Volatile
    private var cachedIndex: PhotoPlaceIndex? = null
    @Volatile
    private var cachedLocationVersion = -1

    suspend fun current(): PhotoPlaceIndex {
        val currentEntries = locationIndex.current().entries
        val version = currentEntries.hashCode()
        cachedIndex?.let { if (cachedLocationVersion == version) return it }

        return mutex.withLock {
            cachedIndex?.let { if (cachedLocationVersion == version) return@withLock it }
            val mapData = mapDataRepository.load()
            val index = PhotoPlaceIndex(currentEntries, mapData.places)
            cachedIndex = index
            cachedLocationVersion = version
            index
        }
    }

    suspend fun idsMatching(term: String): Set<Long> = current().idsMatching(term)
}
