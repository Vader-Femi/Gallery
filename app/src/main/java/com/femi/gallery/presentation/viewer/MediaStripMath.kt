package com.femi.gallery.presentation.viewer

import kotlin.math.abs

/** The decisions of the media strip that need no Compose, so they can be tested on their own. */
internal object MediaStripMath {

    /** Pages further apart than this are jumped to; closer ones are animated to. */
    const val MAX_ANIMATED_DISTANCE = 4

    /**
     * The index of the item whose middle is closest to the middle of the viewport, or null when no
     * item is visible. [indices], [offsets] and [sizes] are parallel arrays describing the visible
     * items; the offsets and the viewport are in the same coordinates, whatever they are.
     */
    fun centeredIndex(
        indices: IntArray,
        offsets: IntArray,
        sizes: IntArray,
        viewportStart: Int,
        viewportEnd: Int,
    ): Int? {
        if (indices.isEmpty()) return null
        val viewportMiddle = (viewportStart + viewportEnd) / 2.0
        var best = 0
        var bestDistance = Double.MAX_VALUE
        for (i in indices.indices) {
            val distance = abs(offsets[i] + sizes[i] / 2.0 - viewportMiddle)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return indices[best]
    }

    /** Whether moving the pager from page [from] to page [to] should animate (otherwise it jumps). */
    fun shouldAnimate(from: Int, to: Int): Boolean = abs(to - from) <= MAX_ANIMATED_DISTANCE

    /**
     * Calculates the expanded filmstrip width (in dp) for a video based on its duration in milliseconds.
     * Longer videos scale to a wider filmstrip.
     */
    fun calculateExpandedWidth(durationMs: Long): Float {
        if (durationMs <= 0L) return 160f
        val seconds = durationMs / 1000f
        return (100f + seconds * 6f).coerceIn(120f, 8000f)
    }

    /**
     * How many picture tiles to lay along an expanded filmstrip [widthDp] wide. About one per 36 dp, but
     * never more than [MAX_FILM_FRAMES]: every tile is a composable with an image, and a long video's strip
     * can be thousands of dp wide, so the tiles get wider instead of more numerous.
     */
    fun filmFrameCount(widthDp: Float): Int = (widthDp / FILM_FRAME_DP).toInt().coerceIn(MIN_FILM_FRAMES, MAX_FILM_FRAMES)

    private const val FILM_FRAME_DP = 36f
    private const val MIN_FILM_FRAMES = 3
    const val MAX_FILM_FRAMES = 40
}
