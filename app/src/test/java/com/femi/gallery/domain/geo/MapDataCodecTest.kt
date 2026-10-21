package com.femi.gallery.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapDataCodecTest {

    private val square = listOf(
        GeoPoint(10.0, 20.0), GeoPoint(10.0, 30.0), GeoPoint(0.0, 30.0), GeoPoint(0.0, 20.0),
    )

    @Test
    fun polylinesRoundTrip() {
        val across = listOf(GeoPoint(-17.5, 179.9999), GeoPoint(-17.6, -179.9999), GeoPoint(80.0, 0.0))
        val decoded = MapDataCodec.decodePolylines(MapDataCodec.encodePolylines(listOf(square, across)))!!
        assertEquals(2, decoded.size)
        assertEquals(4, decoded[0].size)
        assertEquals(Mercator.x(30.0), decoded[0].xs[1], 1e-9)
        assertEquals(Mercator.y(10.0), decoded[0].ys[0], 1e-9)
        // big jumps (across the antimeridian) survive the delta encoding
        assertEquals(Mercator.x(-179.9999), decoded[1].xs[1], 1e-9)
        assertEquals(Mercator.y(80.0), decoded[1].ys[2], 1e-9)
    }

    @Test
    fun polylinesKnowTheirBounds() {
        val line = MapDataCodec.decodePolylines(MapDataCodec.encodePolylines(listOf(square)))!!.single()
        assertEquals(Mercator.x(20.0), line.minX, 1e-9)
        assertEquals(Mercator.x(30.0), line.maxX, 1e-9)
        assertEquals(Mercator.y(10.0), line.minY, 1e-9) // north is the small y
        assertEquals(Mercator.y(0.0), line.maxY, 1e-9)
    }

    @Test
    fun noPolylinesRoundTrip() {
        assertEquals(0, MapDataCodec.decodePolylines(MapDataCodec.encodePolylines(emptyList()))!!.size)
    }

    @Test
    fun damagedPolylinesDecodeToNull() {
        val good = MapDataCodec.encodePolylines(listOf(square, square))
        assertNotNull(MapDataCodec.decodePolylines(good))
        assertNull(MapDataCodec.decodePolylines(ByteArray(0)))
        assertNull(MapDataCodec.decodePolylines(good.copyOf(good.size - 3)))
        assertNull(MapDataCodec.decodePolylines(good.also { it[0] = 1 }.copyOf()))
        val bigCount = MapDataCodec.encodePolylines(listOf(square)).also { it[8] = 0x7F }
        assertNull(MapDataCodec.decodePolylines(bigCount))
    }

    @Test
    fun placesRoundTripWithAccentsAndComeOutLargestFirst() {
        val places = listOf(
            MapPlace("Zürich", 47.3769, 8.5417, 400),
            MapPlace("São Paulo", -23.5505, -46.6333, 12_000),
            MapPlace("Lagos", 6.4541, 3.3947, 7_000),
        )
        val decoded = MapDataCodec.decodePlaces(MapDataCodec.encodePlaces(places))!!
        assertEquals(listOf("São Paulo", "Lagos", "Zürich"), decoded.map { it.name })
        assertEquals(6.4541, decoded[1].latitude, 1e-4)
        assertEquals(3.3947, decoded[1].longitude, 1e-4)
        assertEquals(7_000, decoded[1].population)
    }

    @Test
    fun damagedPlacesDecodeToNull() {
        val good = MapDataCodec.encodePlaces(listOf(MapPlace("Lagos", 6.45, 3.39, 7_000)))
        assertNull(MapDataCodec.decodePlaces(good.copyOf(good.size - 2)))
        assertNull(MapDataCodec.decodePlaces(byteArrayOf(1, 2, 3)))
    }

    // The data files that ship with the app, as written by tools/build_map_data.py.

    private fun asset(name: String): ByteArray? =
        listOf(File("src/main/assets/map/$name"), File("app/src/main/assets/map/$name"))
            .firstOrNull { it.exists() }?.readBytes()

    @Test
    fun theShippedLandIsComplete() {
        val land = MapDataCodec.decodePolylines(asset("land.bin") ?: return)!!
        assertTrue(land.size > 1_000)
        assertTrue(land.all { it.size >= 3 && it.minX >= 0.0 && it.maxX <= 1.0 && it.minY >= 0.0 && it.maxY <= 1.0 })
        assertTrue(land.sumOf { it.size } > 50_000)
    }

    @Test
    fun theShippedLandPutsPlacesOnLandAndSeaInTheSea() {
        val land = MapDataCodec.decodePolylines(asset("land.bin") ?: return)!!
        fun isLand(lat: Double, lon: Double): Boolean {
            val x = Mercator.x(lon)
            val y = Mercator.y(lat)
            var inside = false // even-odd rule over every outline, as the map draws it
            for (ring in land) {
                if (x < ring.minX || x > ring.maxX || y < ring.minY || y > ring.maxY) continue
                var j = ring.size - 1
                for (i in 0 until ring.size) {
                    if ((ring.ys[i] > y) != (ring.ys[j] > y) &&
                        x < (ring.xs[j] - ring.xs[i]) * (y - ring.ys[i]) / (ring.ys[j] - ring.ys[i]) + ring.xs[i]
                    ) {
                        inside = !inside
                    }
                    j = i
                }
            }
            return inside
        }
        assertTrue("Kano", isLand(12.0, 8.5))
        assertTrue("Paris", isLand(48.85, 2.35))
        assertTrue("Sydney inland", isLand(-33.0, 148.0))
        assertFalse("Gulf of Guinea", isLand(0.0, 3.0))
        assertFalse("mid-Atlantic", isLand(30.0, -40.0))
        assertFalse("Caspian Sea", isLand(42.0, 50.5))
    }

    @Test
    fun theShippedBordersAndPlacesAreComplete() {
        val borders = MapDataCodec.decodePolylines(asset("borders.bin") ?: return)!!
        assertTrue(borders.size > 300 && borders.all { it.size >= 2 })
        val places = MapDataCodec.decodePlaces(asset("places.bin") ?: return)!!
        assertTrue(places.size > 1_000)
        assertTrue(places.zipWithNext().all { (a, b) -> a.population >= b.population })
        val lagos = places.first { it.name == "Lagos" }
        assertEquals(6.4, lagos.latitude, 0.3)
        assertEquals(3.4, lagos.longitude, 0.3)
        assertTrue(places.any { it.name == "London" })
    }
}
