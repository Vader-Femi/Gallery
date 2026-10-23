package com.femi.gallery.presentation.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStripMathTest {

    private fun centered(offsets: List<Int>, size: Int, start: Int, end: Int, firstIndex: Int = 0) =
        MediaStripMath.centeredIndex(
            indices = IntArray(offsets.size) { firstIndex + it },
            offsets = offsets.toIntArray(),
            sizes = IntArray(offsets.size) { size },
            viewportStart = start,
            viewportEnd = end,
        )

    @Test
    fun theItemInTheMiddleOfTheViewportIsTheCenteredOne() {
        // Items 40 wide with 4 between them in a viewport 0..200: the middle is 100.
        val offsets = listOf(-40, 4, 48, 92, 136, 180)
        assertEquals(3, centered(offsets, 40, 0, 200)) // spans 92..132, middle 112
    }

    @Test
    fun anItemExactlyOnTheMiddleWins() {
        assertEquals(7, centered(listOf(40, 84, 128), 40, 0, 200, firstIndex = 6)) // 84..124, middle 104 vs 100
    }

    @Test
    fun scrollingAHalfItemFurtherMovesTheCenterToTheNextItem() {
        val before = centered(listOf(60, 104, 148), 40, 0, 200) // middles 80, 124, 168
        val after = centered(listOf(40, 84, 128), 40, 0, 200) // middles 60, 104, 148
        assertEquals(0, before)
        assertEquals(1, after)
    }

    @Test
    fun theViewportCanBeInAnyCoordinates() {
        // Same layout shifted into negative content padding coordinates.
        assertEquals(3, centered(listOf(-140, -96, -52, -8, 36, 80), 40, -100, 100))
    }

    @Test
    fun noVisibleItemsMeansNoCenter() {
        assertNull(centered(emptyList(), 40, 0, 200))
    }

    @Test
    fun nearbyPagesAnimateAndFarOnesJump() {
        assertTrue(MediaStripMath.shouldAnimate(10, 11))
        assertTrue(MediaStripMath.shouldAnimate(10, 6))
        assertFalse(MediaStripMath.shouldAnimate(10, 5))
        assertFalse(MediaStripMath.shouldAnimate(0, 400))
        assertTrue(MediaStripMath.shouldAnimate(3, 3))
    }
}
