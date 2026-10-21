package com.femi.gallery.domain.geo

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * A rectangle on the map, in degrees. The edges are inclusive. When [west] is greater than [east]
 * the box crosses the antimeridian (the 180th meridian) and covers the longitudes outside
 * `east..west`.
 *
 * A box can travel as text (`in:south,west,north,east`) so that "the photos taken here" can be an
 * ordinary search query, like a range of days is.
 */
data class GeoBox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    val crossesAntimeridian: Boolean get() = west > east

    fun contains(latitude: Double, longitude: Double): Boolean {
        if (latitude < south || latitude > north) return false
        return if (west <= east) longitude in west..east else longitude >= west || longitude <= east
    }

    /**
     * The search text that [parse] turns back into this box. Edges are rounded outwards to six
     * decimals (about 10 cm), so a point on an edge is still inside after the round trip.
     */
    fun toQuery(): String {
        val s = (floor(south * SCALE) / SCALE).coerceIn(-90.0, 90.0)
        val w = (floor(west * SCALE) / SCALE).coerceIn(-180.0, 180.0)
        val n = (ceil(north * SCALE) / SCALE).coerceIn(-90.0, 90.0)
        val e = (ceil(east * SCALE) / SCALE).coerceIn(-180.0, 180.0)
        return "$QUERY_PREFIX${format(s)},${format(w)},${format(n)},${format(e)}"
    }

    companion object {
        const val QUERY_PREFIX = "in:"
        private const val SCALE = 1_000_000.0

        private fun format(value: Double): String = String.format(Locale.ROOT, "%.6f", value)

        /** Reads `south,west,north,east` (the part after `in:`); null if it is not a valid box. */
        fun parse(text: String): GeoBox? {
            val parts = text.split(',')
            if (parts.size != 4) return null
            val values = parts.map { it.trim().toDoubleOrNull() ?: return null }
            val (south, west, north, east) = values
            if (values.any { it.isNaN() || it.isInfinite() }) return null
            if (south !in -90.0..90.0 || north !in -90.0..90.0 || south > north) return null
            if (west !in -180.0..180.0 || east !in -180.0..180.0) return null
            return GeoBox(south, west, north, east)
        }
    }
}
