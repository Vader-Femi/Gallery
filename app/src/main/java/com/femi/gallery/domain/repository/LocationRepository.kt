package com.femi.gallery.domain.repository

import com.femi.gallery.domain.geo.LocatedMedia
import com.femi.gallery.domain.geo.LocationScanState
import kotlinx.coroutines.flow.StateFlow

interface LocationRepository {
    /** Progress of the background scan. */
    val scan: StateFlow<LocationScanState>

    /**
     * Starts reading the places of photos that have not been read yet, in the background. Does
     * nothing when a scan is already running or nothing changed since the last one.
     */
    fun startScan()

    /** Photos that have a place, newest first, as known right now. */
    suspend fun locatedMedia(): LocatedMedia
}
