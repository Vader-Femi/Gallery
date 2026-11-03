package com.femi.gallery.domain.similar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DifferenceHashTest {
    /** A picture whose grey value is [f] of the position (0 to 1 across, 0 to 1 down). */
    private fun picture(width: Int, height: Int, f: (Double, Double) -> Int): IntArray =
        IntArray(width * height) { i -> f((i % width + 0.5) / width, (i / width + 0.5) / height).coerceIn(0, 255) }

    private val rising = { x: Double, _: Double -> (x * 255).toInt() }

    @Test
    fun aRisingGradientHasEveryBitSet() {
        assertEquals(-1L, DifferenceHash.fromLuma(picture(90, 80, rising), 90, 80))
    }

    @Test
    fun aFallingGradientHasNoBitSet() {
        assertEquals(0L, DifferenceHash.fromLuma(picture(90, 80) { x, _ -> 255 - (x * 255).toInt() }, 90, 80))
    }

    @Test
    fun theSamePictureAtAnotherSizeGivesTheSameHash() {
        val big = DifferenceHash.fromLuma(picture(360, 320) { x, y -> (x * 200 + y * 50).toInt() + (if (x > 0.5) 0 else 30) }, 360, 320)
        val small = DifferenceHash.fromLuma(picture(72, 64) { x, y -> (x * 200 + y * 50).toInt() + (if (x > 0.5) 0 else 30) }, 72, 64)
        assertNotNull(big)
        assertTrue(DifferenceHash.distance(big!!, small!!) <= 2)
    }

    @Test
    fun aBrighterCopyGivesTheSameHash() {
        val base = picture(90, 80) { x, y -> (x * x * 180 + y * 40).toInt() }
        val brighter = IntArray(base.size) { (base[it] + 25).coerceAtMost(255) }
        assertEquals(DifferenceHash.fromLuma(base, 90, 80), DifferenceHash.fromLuma(brighter, 90, 80))
    }

    @Test
    fun aBlankPictureHasNoHash() {
        assertNull(DifferenceHash.fromLuma(IntArray(90 * 80) { 0 }, 90, 80))
        assertNull(DifferenceHash.fromLuma(IntArray(90 * 80) { 255 }, 90, 80))
        assertNull(DifferenceHash.fromLuma(picture(90, 80) { x, _ -> 100 + (x * 5).toInt() }, 90, 80))
    }

    @Test
    fun aPictureThatIsTooSmallOrTooShortOfDataHasNoHash() {
        assertNull(DifferenceHash.fromLuma(IntArray(8 * 8), 8, 8))
        assertNull(DifferenceHash.fromLuma(IntArray(10), 90, 80))
    }

    @Test
    fun differentPicturesAreFarApart() {
        val a = DifferenceHash.fromLuma(picture(90, 80, rising), 90, 80)!!
        val b = DifferenceHash.fromLuma(picture(90, 80) { x, _ -> 255 - (x * 255).toInt() }, 90, 80)!!
        assertEquals(64, DifferenceHash.distance(a, b))
        assertEquals(0, DifferenceHash.distance(a, a))
    }

    @Test
    fun greyValueFollowsTheEyesWeighing() {
        assertEquals(0, DifferenceHash.luma(0xFF000000.toInt()))
        assertEquals(255, DifferenceHash.luma(0xFFFFFFFF.toInt()))
        assertTrue(DifferenceHash.luma(0xFF00FF00.toInt()) > DifferenceHash.luma(0xFFFF0000.toInt()))
        assertTrue(DifferenceHash.luma(0xFFFF0000.toInt()) > DifferenceHash.luma(0xFF0000FF.toInt()))
    }
}
