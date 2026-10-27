package com.femi.gallery.domain.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StraightenMathTest {

    @Test
    fun notTurnedKeepsEverything() {
        assertEquals(1f, StraightenMath.keptScale(4000, 3000, 0f), 1e-6f)
        assertEquals(4000 to 3000, StraightenMath.boundingSize(4000, 3000, 0f))
    }

    @Test
    fun aSquareTurnedFortyFiveDegreesKeepsAboutSeventyOnePercent() {
        assertEquals(0.7071f, StraightenMath.keptScale(1000, 1000, 45f), 1e-3f)
        assertEquals(0.7071f, StraightenMath.keptScale(1000, 1000, -45f), 1e-3f)
    }

    @Test
    fun theKeptRectangleKeepsTheShapeOfThePicture() {
        val (left, top, w, h) = StraightenMath.trim(4000, 3000, 4500, 4200, 10f)
        assertEquals(4000f / 3000f, w.toFloat() / h, 0.01f)
        assertTrue(left >= 0 && top >= 0 && left + w <= 4500 && top + h <= 4200)
    }

    @Test
    fun theKeptRectangleIsCentred() {
        val (left, top, w, h) = StraightenMath.trim(1000, 1000, 1400, 1400, 20f)
        assertTrue(Math.abs(1400 - (left + w) - left) <= 1)
        assertTrue(Math.abs(1400 - (top + h) - top) <= 1)
    }

    @Test
    fun theKeptRectangleReallyHasNoEmptyCorner() {
        // Rotate the kept rectangle's corners back by the angle: they must lie inside the original picture.
        val width = 3000
        val height = 2000
        val degrees = 12f
        val scale = StraightenMath.keptScale(width, height, degrees)
        val a = Math.toRadians(degrees.toDouble())
        val hx = width * scale / 2.0
        val hy = height * scale / 2.0
        for (sx in listOf(-1, 1)) for (sy in listOf(-1, 1)) {
            val x = sx * hx
            val y = sy * hy
            val ox = x * Math.cos(a) + y * Math.sin(a)
            val oy = -x * Math.sin(a) + y * Math.cos(a)
            assertTrue("corner x $ox", Math.abs(ox) <= width / 2.0 + 1e-6)
            assertTrue("corner y $oy", Math.abs(oy) <= height / 2.0 + 1e-6)
        }
    }

    @Test
    fun boundingBoxGrowsWithTheAngle() {
        val (w, h) = StraightenMath.boundingSize(4000, 3000, 10f)
        assertTrue(w > 4000 && h > 3000)
    }

    @Test
    fun emptyPictureIsLeftAlone() {
        assertEquals(1f, StraightenMath.keptScale(0, 0, 10f), 0f)
    }
}
