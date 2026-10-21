package com.femi.gallery.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceVisibilityTest {

    @Test
    fun biggerPlacesAppearAtLowerZoomLevels() {
        val sizes = listOf(20_000, 6_000, 3_000, 1_500, 700, 300, 50)
        val zooms = sizes.map { PlaceVisibility.minZoom(it) }
        assertTrue(zooms.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun theThresholdsAreInclusive() {
        assertEquals(2.0, PlaceVisibility.minZoom(10_000), 0.0)
        assertEquals(PlaceVisibility.minZoom(1_000), PlaceVisibility.minZoom(1_500), 0.0)
        assertEquals(7.5, PlaceVisibility.minZoom(0), 0.0)
    }

    @Test
    fun theBoundingBoxOfThePhotosCoversAllOfThem() {
        val media = LocatedMedia(
            ids = longArrayOf(1, 2, 3),
            latitudes = doubleArrayOf(6.5, 51.5, -33.9),
            longitudes = doubleArrayOf(3.4, -0.1, 151.2),
            dates = longArrayOf(1, 2, 3),
        )
        val box = media.boundingBox()!!
        assertEquals(GeoBox(south = -33.9, west = -0.1, north = 51.5, east = 151.2), box)
        assertEquals(null, LocatedMedia.Empty.boundingBox())
    }
}
