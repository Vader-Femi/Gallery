package com.femi.gallery.domain.geo

import com.femi.gallery.domain.model.MediaOrder

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

    /** The smallest box around all the photos, or null when there are none. */
    fun boundingBox(): GeoBox? {
        if (size == 0) return null
        var south = Double.MAX_VALUE
        var north = -Double.MAX_VALUE
        var west = Double.MAX_VALUE
        var east = -Double.MAX_VALUE
        for (i in 0 until size) {
            if (latitudes[i] < south) south = latitudes[i]
            if (latitudes[i] > north) north = latitudes[i]
            if (longitudes[i] < west) west = longitudes[i]
            if (longitudes[i] > east) east = longitudes[i]
        }
        return GeoBox(south, west, north, east)
    }

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

        /** The photos of [order] (newest first) that have a place in [index], with their dates. */
        fun from(order: MediaOrder, index: PlaceIndex): LocatedMedia {
            val ids = LongArray(order.size)
            val latitudes = DoubleArray(order.size)
            val longitudes = DoubleArray(order.size)
            val dates = LongArray(order.size)
            var count = 0
            for (i in 0 until order.size) {
                val place = index.placeOf(order.ids[i]) ?: continue
                ids[count] = order.ids[i]
                latitudes[count] = place.latitude
                longitudes[count] = place.longitude
                dates[count] = order.datesMillis[i] / 1000
                count++
            }
            return LocatedMedia(
                ids.copyOf(count),
                latitudes.copyOf(count),
                longitudes.copyOf(count),
                dates.copyOf(count),
            )
        }
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
