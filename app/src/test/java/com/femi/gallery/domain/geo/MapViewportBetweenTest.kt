package com.femi.gallery.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewportBetweenTest {

    private val width = 1080.0
    private val height = 2000.0
    private val lagos = MapViewport(centerLatitude = 6.5, centerLongitude = 3.4, zoom = 11.0)
    private val london = MapViewport(centerLatitude = 51.5, centerLongitude = -0.1, zoom = 4.0)

    @Test
    fun theEndsAreReproducedExactly() {
        assertEquals(lagos, MapViewport.between(lagos, london, 0.0, width, height))
        assertEquals(london, MapViewport.between(lagos, london, 1.0, width, height))
    }

    @Test
    fun outOfRangeFractionsStayAtTheEnds() {
        assertEquals(lagos, MapViewport.between(lagos, london, -3.0, width, height))
        assertEquals(london, MapViewport.between(lagos, london, 7.0, width, height))
    }

    @Test
    fun theZoomChangesEvenly() {
        val half = MapViewport.between(lagos, london, 0.5, width, height)
        assertEquals(7.5, half.zoom, 1e-9)
        val quarter = MapViewport.between(lagos, london, 0.25, width, height)
        assertEquals(9.25, quarter.zoom, 1e-9)
    }

    @Test
    fun theCentreStaysBetweenTheTwoPlaces() {
        val half = MapViewport.between(lagos, london, 0.5, width, height)
        assertTrue(half.centerLatitude > 6.5 && half.centerLatitude < 51.5)
        assertTrue(half.centerLongitude > -0.1 && half.centerLongitude < 3.4)
    }

    @Test
    fun theCentreMovesTowardsTheTarget() {
        var last = Double.MAX_VALUE
        for (step in 1..10) {
            val view = MapViewport.between(lagos, london, step / 10.0, width, height)
            assertTrue("step $step", Math.abs(view.centerLatitude - london.centerLatitude) < last)
            last = Math.abs(view.centerLatitude - london.centerLatitude)
        }
    }

    @Test
    fun theSameViewStaysPut() {
        val same = MapViewport.between(lagos, lagos, 0.4, width, height)
        assertEquals(lagos.zoom, same.zoom, 1e-9)
        assertEquals(lagos.centerLatitude, same.centerLatitude, 1e-9)
        assertEquals(lagos.centerLongitude, same.centerLongitude, 1e-9)
    }
}
