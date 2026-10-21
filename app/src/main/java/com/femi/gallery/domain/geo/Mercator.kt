package com.femi.gallery.domain.geo

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sinh

/**
 * The Web Mercator projection that web maps use, onto the unit square: x runs 0 to 1 from west to
 * east and y runs 0 to 1 from north to south. Latitudes beyond [MAX_LATITUDE] are clamped.
 */
object Mercator {
    const val MAX_LATITUDE = 85.05112878

    fun x(longitude: Double): Double = (longitude + 180.0) / 360.0

    fun y(latitude: Double): Double {
        val s = sin(Math.toRadians(latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)))
        return (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)).coerceIn(0.0, 1.0)
    }

    fun longitude(x: Double): Double = x * 360.0 - 180.0

    fun latitude(y: Double): Double = Math.toDegrees(atan(sinh(PI * (1 - 2 * y))))
}
