package com.femi.gallery.domain.edit

/**
 * Unsharp masking, a row at a time so a 12-megapixel photo never needs a second full-size pixel
 * array. Each colour channel becomes `centre + amount * (centre - average of its 4 neighbours)`,
 * which exaggerates edges. Pixels are packed ARGB ints; alpha is kept.
 */
object Sharpen {

    /** The strongest effect, reached at a slider value of 1. */
    const val MAX_AMOUNT = 1.5f

    /** The weight to give the slider position [value] (0..1). */
    fun amountFor(value: Float): Float = value.coerceIn(0f, 1f) * MAX_AMOUNT

    /**
     * Sharpens the row [current] into [out], looking at the rows [above] and [below] it. Pass the
     * row itself for [above] on the first row and for [below] on the last, so edges stay unchanged.
     */
    fun row(above: IntArray, current: IntArray, below: IntArray, out: IntArray, amount: Float) {
        val width = current.size
        for (x in 0 until width) {
            val left = current[if (x > 0) x - 1 else x]
            val right = current[if (x < width - 1) x + 1 else x]
            val c = current[x]
            val u = above[x]
            val d = below[x]
            out[x] = (c and ALPHA) or
                (channel(c, left, right, u, d, 16, amount) shl 16) or
                (channel(c, left, right, u, d, 8, amount) shl 8) or
                channel(c, left, right, u, d, 0, amount)
        }
    }

    private fun channel(c: Int, l: Int, r: Int, u: Int, d: Int, shift: Int, amount: Float): Int {
        val centre = (c shr shift) and 0xFF
        val around = (((l shr shift) and 0xFF) + ((r shr shift) and 0xFF) +
            ((u shr shift) and 0xFF) + ((d shr shift) and 0xFF)) / 4f
        val value = centre + amount * (centre - around)
        return when {
            value < 0f -> 0
            value > 255f -> 255
            else -> (value + 0.5f).toInt()
        }
    }

    private const val ALPHA = 0xFF shl 24
}
