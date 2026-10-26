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
