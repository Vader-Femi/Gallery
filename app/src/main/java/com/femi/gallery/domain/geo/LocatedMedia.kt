package com.femi.gallery.domain.geo

/**
 * The photos that have a known place, as parallel arrays (a library can hold tens of thousands, so
 * this avoids an object per photo). Entry `i` is the photo [ids]`[i]`, taken at
 * ([latitudes]`[i]`, [longitudes]`[i]`) at [dates]`[i]` (epoch seconds, used to pick the newest).
 */
class LocatedMedia(
    val ids: LongArray,
    val latitudes: DoubleArray,
    val longitudes: DoubleArray,
    val dates: LongArray,
) {
    init {
        require(latitudes.size == ids.size && longitudes.size == ids.size && dates.size == ids.size) {
            "Arrays must have the same size"
        }
    }

    val size: Int get() = ids.size

    /** Projected positions on the unit square, worked out once and reused by every zoom level. */
    internal val xs: DoubleArray = DoubleArray(size) { Mercator.x(longitudes[it]) }
    internal val ys: DoubleArray = DoubleArray(size) { Mercator.y(latitudes[it]) }

    /** The ids of the photos taken inside [box]. */
    fun idsIn(box: GeoBox): Set<Long> {
        val result = HashSet<Long>()
        for (i in 0 until size) {
            if (box.contains(latitudes[i], longitudes[i])) result.add(ids[i])
        }
        return result
    }

    companion object {
        val Empty = LocatedMedia(LongArray(0), DoubleArray(0), DoubleArray(0), LongArray(0))
    }
}

/** A group of photos that are close together at the current zoom level. */
data class GeoCluster(
    val latitude: Double,
    val longitude: Double,
    val count: Int,
    /** The smallest box around the group; searching it finds exactly these photos. */
    val box: GeoBox,
    /** The newest photo of the group, shown as the cover. */
    val coverId: Long,
)
