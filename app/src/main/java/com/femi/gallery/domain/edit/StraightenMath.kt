package com.femi.gallery.domain.edit

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Straightening turns the picture by a few degrees. The turned picture no longer fills its bounding
 * box (the corners are empty), so it is trimmed to the largest rectangle with the picture's own
 * shape that fits inside it, as Google Photos does.
 */
object StraightenMath {

    /**
     * The width and height of the picture after rotating a [width] x [height] picture by
     * [degrees]: the bounding box of the turned picture, corners included.
     */
    fun boundingSize(width: Int, height: Int, degrees: Float): Pair<Int, Int> {
        val a = Math.toRadians(abs(degrees).toDouble())
        val s = abs(sin(a))
        val c = abs(cos(a))
        val w = width * c + height * s
        val h = width * s + height * c
        return Math.ceil(w - 1e-6).toInt() to Math.ceil(h - 1e-6).toInt()
    }

    /**
     * Fraction (0..1] of the picture's width and height that stays when it is turned by [degrees]
     * and trimmed to the biggest same-shaped rectangle with no empty corner. 1 when not turned.
     */
    fun keptScale(width: Int, height: Int, degrees: Float): Float {
        if (width <= 0 || height <= 0) return 1f
        val a = Math.toRadians(abs(degrees).toDouble())
        val s = abs(sin(a))
        val c = abs(cos(a))
        val longer = max(width, height).toDouble()
        val shorter = min(width, height).toDouble()
        val scale = 1.0 / (c + (longer / shorter) * s)
        return scale.coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * The part of the turned picture's bounding box ([boundingWidth] x [boundingHeight], see
     * [boundingSize]) to keep: centred, and [keptScale] times the picture's original size.
     * Returned as pixel bounds (left, top, width, height).
     */
    fun trim(
        originalWidth: Int,
        originalHeight: Int,
        boundingWidth: Int,
        boundingHeight: Int,
        degrees: Float,
    ): IntArray {
        val scale = keptScale(originalWidth, originalHeight, degrees)
        val w = (originalWidth * scale).toInt().coerceIn(1, boundingWidth)
        val h = (originalHeight * scale).toInt().coerceIn(1, boundingHeight)
        return intArrayOf((boundingWidth - w) / 2, (boundingHeight - h) / 2, w, h)
    }
}
