package com.femi.gallery.data.location

import com.femi.gallery.domain.geo.LocationEntry
import com.femi.gallery.domain.geo.PlaceIndex
import com.femi.gallery.domain.repository.LocationStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The scan results held in memory: read from the store the first time they are needed, and replaced
 * whenever the scan publishes more. Anything that filters by place (search, the map) asks this.
 */
@Singleton
class LocationIndex @Inject constructor(
    private val store: LocationStore,
) {
    private val mutex = Mutex()

    @Volatile
    private var loaded: PlaceIndex? = null

    suspend fun current(): PlaceIndex =
        loaded ?: mutex.withLock { loaded ?: PlaceIndex(store.load()).also { loaded = it } }

    suspend fun publish(entries: List<LocationEntry>) {
        mutex.withLock { loaded = PlaceIndex(entries) }
    }
}
