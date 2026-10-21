package com.femi.gallery.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewportTest {

    private val width = 1080.0
    private val height = 2000.0
    private val eps = 1e-6

    @Test
    fun mercatorRoundTrips() {
        for (lat in listOf(-80.0, -33.5, 0.0, 6.52, 51.5, 80.0)) {
            assertEquals(lat, Mercator.latitude(Mercator.y(lat)), 1e-9)
        }
        for (lon in listOf(-180.0, -73.9, 0.0, 3.38, 179.9)) {
            assertEquals(lon, Mercator.longitude(Mercator.x(lon)), 1e-9)
        }
        assertEquals(0.5, Mercator.x(0.0), eps)
        assertEquals(0.5, Mercator.y(0.0), eps)
    }

    @Test
    fun latitudesBeyondTheLimitAreClamped() {
        assertEquals(0.0, Mercator.y(90.0), 1e-6)
        assertEquals(1.0, Mercator.y(-90.0), 1e-6)
    }

    @Test
    fun theCentreIsInTheMiddleOfTheScreen() {
        val viewport = MapViewport(6.52, 3.38, 10.0)
        assertEquals(width / 2, viewport.screenX(3.38, width), eps)
        assertEquals(height / 2, viewport.screenY(6.52, height), eps)
    }

    @Test
    fun screenAndMapPositionsRoundTrip() {
        val viewport = MapViewport(48.0, 11.0, 7.0)
        val x = viewport.screenX(12.5, width)
        val y = viewport.screenY(47.0, height)
        assertEquals(12.5, viewport.longitudeAt(x, width), 1e-9)
        assertEquals(47.0, viewport.latitudeAt(y, height), 1e-9)
    }

    @Test
    fun eachZoomLevelDoublesTheMap() {
        val a = MapViewport(0.0, 0.0, 4.0)
        val b = MapViewport(0.0, 0.0, 5.0)
        val dxA = a.screenX(10.0, width) - a.screenX(0.0, width)
        val dxB = b.screenX(10.0, width) - b.screenX(0.0, width)
        assertEquals(dxA * 2, dxB, 1e-6)
    }

    @Test
    fun draggingMovesTheMapWithTheFinger() {
        val viewport = MapViewport(6.5, 3.4, 8.0)
        val before = viewport.screenX(3.5, width)
        val moved = viewport.panned(dx = 120.0, dy = 0.0, width = width, height = height)
        assertEquals(before + 120.0, moved.screenX(3.5, width), 1e-6)
    }

    @Test
    fun panningStopsAtTheEdgeOfTheWorld() {
        val viewport = MapViewport(0.0, 170.0, 5.0)
        val moved = viewport.panned(dx = -100_000.0, dy = 100_000.0, width = width, height = height)
        val box = moved.visibleBox(width, height)
        assertTrue(box.east <= 180.0 + 1e-9)
        assertTrue(box.north <= Mercator.MAX_LATITUDE + 1e-9)
        assertTrue(box.south >= -Mercator.MAX_LATITUDE - 1e-9)
    }

    @Test
    fun zoomingKeepsThePointUnderTheFingerInPlace() {
        val viewport = MapViewport(6.5, 3.4, 8.0)
        val ax = 300.0
        val ay = 700.0
        val longitude = viewport.longitudeAt(ax, width)
        val latitude = viewport.latitudeAt(ay, height)
        val zoomed = viewport.zoomedAbout(ax, ay, 9.3, width, height)
        assertEquals(9.3, zoomed.zoom, eps)
        assertEquals(ax, zoomed.screenX(longitude, width), 1e-4)
        assertEquals(ay, zoomed.screenY(latitude, height), 1e-4)
    }

    @Test
    fun zoomIsLimited() {
        val viewport = MapViewport(6.5, 3.4, 8.0)
        assertEquals(MapViewport.MAX_ZOOM, viewport.zoomedAbout(10.0, 10.0, 99.0, width, height).zoom, eps)
        val out = viewport.zoomedAbout(10.0, 10.0, -5.0, width, height)
        assertEquals(MapViewport.minZoom(width, height), out.zoom, eps)
        // at the lowest zoom the world covers the whole screen
        assertTrue(MapViewport.worldSize(out.zoom) >= height - 1e-6)
    }

    @Test
    fun visibleBoxContainsTheCentreAndGrowsWithAMargin() {
        val viewport = MapViewport(6.5, 3.4, 9.0)
        val box = viewport.visibleBox(width, height)
        assertTrue(box.contains(6.5, 3.4))
        val bigger = viewport.visibleBox(width, height, marginPixels = 200.0)
        assertTrue(bigger.north > box.north && bigger.south < box.south)
        assertTrue(bigger.east > box.east && bigger.west < box.west)
    }

    @Test
    fun fitShowsTheWholeBoxWithinThePadding() {
        val box = GeoBox(south = 6.3, west = 3.1, north = 6.7, east = 3.7)
        val padding = 80.0
        val viewport = MapViewport.fit(box, width, height, padding)
        for ((lat, lon) in listOf(box.south to box.west, box.north to box.east)) {
            val x = viewport.screenX(lon, width)
            val y = viewport.screenY(lat, height)
            assertTrue("x=$x", x >= padding - 1e-6 && x <= width - padding + 1e-6)
            assertTrue("y=$y", y >= padding - 1e-6 && y <= height - padding + 1e-6)
        }
        assertTrue(box.contains(viewport.centerLatitude, viewport.centerLongitude))
    }

    @Test
    fun fitOnASinglePlaceUsesTheClosestZoom() {
        val place = GeoBox(6.5, 3.4, 6.5, 3.4)
        assertEquals(MapViewport.SINGLE_PLACE_ZOOM, MapViewport.fit(place, width, height, 40.0).zoom, eps)
    }

    @Test
    fun fitAcrossTheAntimeridianCentresOnTheOtherSideOfTheWorld() {
        val box = GeoBox(south = -20.0, west = 175.0, north = -15.0, east = -178.0)
        val viewport = MapViewport.fit(box, width, height, 40.0)
        assertTrue(box.contains(viewport.centerLatitude, viewport.centerLongitude))
    }
}
