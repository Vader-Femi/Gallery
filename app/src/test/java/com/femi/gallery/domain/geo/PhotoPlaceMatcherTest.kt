package com.femi.gallery.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoPlaceMatcherTest {

    private val places = listOf(
        MapPlace("Lagos", 6.52, 3.38, 10000000),
        MapPlace("London", 51.5, -0.13, 9000000),
    )

    @Test
    fun testNearestPlaceFound() {
        val nearLagos = GeoPoint(6.50, 3.40)
        assertEquals("lagos", PhotoPlaceMatcher.nearestPlaceName(nearLagos, places))
    }

    @Test
    fun testTooFarIsNull() {
        val farAway = GeoPoint(0.0, 0.0)
        assertNull(PhotoPlaceMatcher.nearestPlaceName(farAway, places, maxDistanceKm = 50.0))
    }
}
