package com.femi.gallery.domain.edit

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMatricesTest {

    private fun pixel(matrix: FloatArray, r: Float, g: Float, b: Float) = ColorMatrices.apply(matrix, r, g, b)

    @Test
    fun untouchedEditIsTheIdentity() {
        assertArrayEquals(ColorMatrices.identity(), EditState().colorMatrix(), 0f)
        assertArrayEquals(floatArrayOf(10f, 120f, 250f), pixel(EditState().colorMatrix(), 10f, 120f, 250f), 0.001f)
    }

    @Test
    fun brightnessAddsLightAndClamps() {
        val brighter = pixel(ColorMatrices.brightness(0.5f), 100f, 100f, 100f)
        assertTrue(brighter[0] > 100f)
        assertEquals(255f, pixel(ColorMatrices.brightness(1f), 250f, 250f, 250f)[0], 0f)
        assertTrue(pixel(ColorMatrices.brightness(-0.5f), 100f, 100f, 100f)[0] < 100f)
    }

    @Test
    fun contrastMovesValuesAwayFromMidGray() {
        val matrix = ColorMatrices.contrast(0.5f)
        assertEquals(128f, pixel(matrix, 128f, 128f, 128f)[0], 0.01f)
        assertTrue(pixel(matrix, 200f, 200f, 200f)[0] > 200f)
        assertTrue(pixel(matrix, 50f, 50f, 50f)[0] < 50f)
    }

    @Test
    fun fullyDesaturatedColorBecomesGray() {
        val gray = pixel(ColorMatrices.saturation(-1f), 200f, 100f, 50f)
        assertEquals(gray[0], gray[1], 0.01f)
        assertEquals(gray[1], gray[2], 0.01f)
    }

    @Test
    fun warmthShiftsRedAndBlueOppositely() {
        val warm = pixel(ColorMatrices.warmth(1f), 100f, 100f, 100f)
        assertTrue(warm[0] > 100f && warm[2] < 100f)
        val cool = pixel(ColorMatrices.warmth(-1f), 100f, 100f, 100f)
        assertTrue(cool[0] < 100f && cool[2] > 100f)
    }

    @Test
    fun sepiaTintsGrayTowardsBrown() {
        val sepia = pixel(ColorMatrices.filter(EditFilter.Sepia), 100f, 100f, 100f)
        assertTrue(sepia[0] > sepia[1] && sepia[1] > sepia[2])
    }

    @Test
    fun monoAndNoirProduceGray() {
        for (filter in listOf(EditFilter.Mono, EditFilter.Noir)) {
            val gray = pixel(ColorMatrices.filter(filter), 220f, 60f, 30f)
            assertEquals(gray[0], gray[1], 0.01f)
            assertEquals(gray[1], gray[2], 0.01f)
        }
    }

    @Test
    fun timesAppliesTheSecondMatrixFirst() {
        val brightenThenDesaturate = ColorMatrices.times(ColorMatrices.saturation(-1f), ColorMatrices.brightness(0.5f))
        val brighter = pixel(ColorMatrices.brightness(0.5f), 200f, 100f, 50f)
        val direct = pixel(ColorMatrices.saturation(-1f), brighter[0], brighter[1], brighter[2])
        assertArrayEquals(direct, pixel(brightenThenDesaturate, 200f, 100f, 50f), 0.05f)
    }

    @Test
    fun everyFilterKeepsAlphaUntouched() {
        for (filter in EditFilter.entries) {
            val matrix = ColorMatrices.filter(filter)
            assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f, 0f), matrix.copyOfRange(15, 20), 0f)
        }
    }
}
