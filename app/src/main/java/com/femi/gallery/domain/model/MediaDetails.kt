package com.femi.gallery.domain.model

data class MediaDetails(
    val item: MediaItem,
    /** e.g. "Google Pixel 9 Pro". */
    val camera: String? = null,
    val aperture: Double? = null,
    val exposureSeconds: Double? = null,
    val iso: Int? = null,
    val focalLengthMm: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
