package com.femi.gallery.domain.model

/**
 * Finds where a photo really is in a paged list.
 *
 * The grid and the viewer each load their own copy of the (ordered) library, and a position alone
 * can point at a different photo in the other copy if the two were loaded at different moments.
 * Screens therefore pass the photo's id along with its position, and use this to settle on the
 * position that actually holds that photo.
 */
object MediaPositions {

    /** "No id known" (ids from MediaStore and the Locked folder are never negative). */
    const val NO_ID: Long = Long.MIN_VALUE

    /** How far from the expected position to look for the photo. */
    const val DEFAULT_SEARCH_DISTANCE = 2_000

    /**
     * The position of the photo with [id], looking first at [preferred] and then at the nearest
     * positions outward (up to [maxDistance] either side), or null if it isn't there.
     *
     * [idAt] returns the id at a position, or null while that position is not loaded yet.
     */
    fun find(
        preferred: Int,
        id: Long,
        count: Int,
        maxDistance: Int = DEFAULT_SEARCH_DISTANCE,
        idAt: (Int) -> Long?,
    ): Int? {
        if (id == NO_ID || count <= 0) return null
        val start = preferred.coerceIn(0, count - 1)
        if (idAt(start) == id) return start
        for (distance in 1..maxDistance) {
            val below = start - distance
            val above = start + distance
            if (below < 0 && above >= count) break
            if (below >= 0 && idAt(below) == id) return below
            if (above < count && idAt(above) == id) return above
        }
        return null
    }
}
