package com.femi.gallery.domain.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VignetteTest {
    @Test
    fun theMiddleIsNeverDarkened() {
        assertEquals(0f, Vignette.alphaAt(0f, 1f), 0f)
        assertEquals(0f, Vignette.alphaAt(Vignette.CLEAR_UNTIL, 1f), 0f)
    }

    @Test
    fun theCornerGetsTheFullEdgeAlpha() {
        assertEquals(Vignette.edgeAlpha(1f), Vignette.alphaAt(1f, 1f), 1e-6f)
    }

    @Test
    fun darkeningGrowsTowardsTheCorner() {
        val a = Vignette.alphaAt(0.6f, 1f)
        val b = Vignette.alphaAt(0.8f, 1f)
        val c = Vignette.alphaAt(1f, 1f)
        assertTrue(a < b && b < c)
    }

    @Test
    fun aStrongerSliderDarkensMore() {
        assertTrue(Vignette.alphaAt(0.9f, 0.8f) > Vignette.alphaAt(0.9f, 0.3f))
    }

    @Test
    fun noSliderNoVignette() {
        assertTrue(Vignette.stops(0f).all { it.second == 0f })
    }

    @Test
    fun stopsRunFromCentreToCorner() {
        val stops = Vignette.stops(1f)
        assertEquals(0f, stops.first().first, 0f)
        assertEquals(1f, stops.last().first, 0f)
        assertTrue(stops.zipWithNext().all { (a, b) -> a.first < b.first && a.second <= b.second })
    }
}
