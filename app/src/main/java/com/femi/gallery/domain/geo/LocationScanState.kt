package com.femi.gallery.domain.geo

enum class LocationScanStatus {
    /** Nothing is being scanned (it is finished, or has not started). */
    Idle,

    Scanning,

    /** Photos cannot be scanned until the user allows access to the location in their photos. */
    NeedsPermission,
}

/** Progress of the background scan that reads where photos were taken. */
data class LocationScanState(
    val status: LocationScanStatus = LocationScanStatus.Idle,
    /** Photos read so far in this scan. */
    val scanned: Int = 0,
    /** Photos this scan has to read. */
    val total: Int = 0,
    /** Photos known to have a place (from this and earlier scans). */
    val placed: Int = 0,
)
