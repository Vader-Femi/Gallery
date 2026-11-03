package com.femi.gallery.domain.similar

/**
 * A 64-bit "difference hash" of a picture: the picture is reduced to a grid of 9 by 8 grey values (each the average
 * of the part of the picture it covers) and every value is compared with its right-hand neighbour. Pictures that
 * look alike (a resized or re-saved copy, the next shot of a burst) give hashes that differ in only a few bits, so
 * [distance] tells how alike two pictures are. Colour and exact size do not matter.
 */
object DifferenceHash {
    const val GRID_WIDTH = 9
    const val GRID_HEIGHT = 8

    /** Pictures whose grey values vary less than this over the whole grid (a blank wall, a black frame) have no hash. */
    const val MIN_CONTRAST = 12

    /**
     * The hash of a picture given as grey values 0 to 255, [width] by [height], row by row; null when the picture is
     * too small or too flat to tell apart from other flat pictures (they would all get the same hash).
     */
    fun fromLuma(luma: IntArray, width: Int, height: Int): Long? {
        if (width < GRID_WIDTH || height < GRID_HEIGHT || luma.size < width * height) return null
        val grid = IntArray(GRID_WIDTH * GRID_HEIGHT)
        for (gy in 0 until GRID_HEIGHT) {
            val y0 = gy * height / GRID_HEIGHT
            val y1 = maxOf(y0 + 1, (gy + 1) * height / GRID_HEIGHT)
            for (gx in 0 until GRID_WIDTH) {
                val x0 = gx * width / GRID_WIDTH
                val x1 = maxOf(x0 + 1, (gx + 1) * width / GRID_WIDTH)
                var sum = 0L
                for (y in y0 until y1) for (x in x0 until x1) sum += luma[y * width + x]
                grid[gy * GRID_WIDTH + gx] = (sum / ((y1 - y0) * (x1 - x0))).toInt()
            }
        }
        if (grid.max() - grid.min() < MIN_CONTRAST) return null
        var hash = 0L
        for (gy in 0 until GRID_HEIGHT) {
            for (gx in 0 until GRID_WIDTH - 1) {
                hash = (hash shl 1) or (if (grid[gy * GRID_WIDTH + gx] < grid[gy * GRID_WIDTH + gx + 1]) 1L else 0L)
            }
        }
        return hash
    }

    /** The grey value (0 to 255) of a colour in ARGB, as the eye weighs red, green and blue. */
    fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }

    /** How many of the 64 bits differ: 0 is the same picture, a few is a very similar one. */
    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}
