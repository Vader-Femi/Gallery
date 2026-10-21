package com.femi.gallery.domain.geo

/** A place on Earth, in degrees. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    val isValid: Boolean
        get() = latitude in -90.0..90.0 && longitude in -180.0..180.0
}
