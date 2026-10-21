package com.femi.gallery.domain.geo

/** A place on Earth, in degrees. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    val isValid: Boolean
        get() = latitude in -90.0..90.0 && longitude in -180.0..180.0

    companion object {
        /**
         * The place stored in a photo, or null when it is missing, impossible, or just (0, 0): some
         * cameras write that when they had no GPS fix, and it would put the photo in the sea.
         */
        fun fromExif(latitude: Double, longitude: Double): GeoPoint? {
            val place = GeoPoint(latitude, longitude)
            return if (!place.isValid || (latitude == 0.0 && longitude == 0.0)) null else place
        }
    }
}
