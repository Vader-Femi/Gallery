package com.femi.gallery.domain.color

import org.junit.Assert.assertTrue
import org.junit.Test

class ColorClassifierTest {

    @Test
    fun testClassifyRed() {
        val width = 10
        val height = 10
        val pixels = IntArray(width * height) { 0xFFFF0000.toInt() } // Pure red
        val tags = ColorClassifier.classify(pixels, width, height)
        assertTrue("Expected red in $tags", tags.contains("red"))
    }

    @Test
    fun testClassifyBlue() {
        val width = 10
        val height = 10
        val pixels = IntArray(width * height) { 0xFF0000FF.toInt() } // Pure blue
        val tags = ColorClassifier.classify(pixels, width, height)
        assertTrue("Expected blue in $tags", tags.contains("blue"))
    }

    @Test
    fun testClassifyBlack() {
        val width = 10
        val height = 10
        val pixels = IntArray(width * height) { 0xFF000000.toInt() } // Pure black
        val tags = ColorClassifier.classify(pixels, width, height)
        assertTrue("Expected black and dark in $tags", tags.contains("black") && tags.contains("dark"))
    }

    @Test
    fun testClassifyWhite() {
        val width = 10
        val height = 10
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() } // Pure white
        val tags = ColorClassifier.classify(pixels, width, height)
        assertTrue("Expected white and light in $tags", tags.contains("white") && tags.contains("light"))
    }
}

class ColorClassifierSharesTest {

    /** A 10x10 picture whose first [greenPixels] pixels are green and the rest white. */
    private fun mostlyWhite(greenPixels: Int) = IntArray(100) { if (it < greenPixels) 0xFF00FF00.toInt() else 0xFFFFFFFF.toInt() }

    @Test
    fun aSmallPatchOfColourIsNotNamed() {
        val tags = ColorClassifier.classify(mostlyWhite(5), 10, 10) // 5 %
        assertTrue("green must not be named for 5 %: $tags", "green" !in tags)
        assertTrue("white" in tags)
    }

    @Test
    fun aClearPatchOfColourIsNamed() {
        val tags = ColorClassifier.classify(mostlyWhite(20), 10, 10) // 20 %
        assertTrue("green" in tags && "white" in tags)
    }

    @Test
    fun aLittleShadowIsNotDark() {
        val pixels = IntArray(100) { if (it < 30) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() } // 30 % black
        val tags = ColorClassifier.classify(pixels, 10, 10)
        assertTrue("dark must need 40 %: $tags", "dark" !in tags)
        assertTrue("black" in tags)
    }

    @Test
    fun aMostlyDarkPictureIsDark() {
        val pixels = IntArray(100) { if (it < 60) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        assertTrue("dark" in ColorClassifier.classify(pixels, 10, 10))
    }

    @Test
    fun emptyInputHasNoColours() {
        assertTrue(ColorClassifier.classify(IntArray(0), 0, 0).isEmpty())
    }
}
