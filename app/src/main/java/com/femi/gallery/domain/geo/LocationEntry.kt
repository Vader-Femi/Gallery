package com.femi.gallery.domain.geo

/**
 * What the scan learned about one photo: where it was taken, or that it has no place at all (so it
 * is not read again). [modified] is the file's DATE_MODIFIED when it was read; a photo that has
 * changed since is read again.
 */
data class LocationEntry(
    val id: Long,
    val modified: Long,
    val place: GeoPoint?,
)
