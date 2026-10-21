package com.femi.gallery.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocationCodecTest {

    private val entries = listOf(
        LocationEntry(id = 28_388L, modified = 1_760_000_000L, place = GeoPoint(6.524379, 3.379206)),
        LocationEntry(id = 5L, modified = 1_700_000_000L, place = null),
        LocationEntry(id = 255_197L, modified = 1_761_234_567L, place = GeoPoint(-33.868820, 151.209296)),
        LocationEntry(id = Long.MAX_VALUE, modified = 0L, place = GeoPoint(-90.0, -180.0)),
    )

    @Test
    fun roundTripsEntriesIncludingPhotosWithoutAPlace() {
        val decoded = LocationCodec.decode(LocationCodec.encode(entries))!!
        assertEquals(entries.size, decoded.size)
        entries.zip(decoded).forEach { (expected, actual) ->
            assertEquals(expected.id, actual.id)
            assertEquals(expected.modified, actual.modified)
            if (expected.place == null) {
                assertNull(actual.place)
            } else {
                assertEquals(expected.place.latitude, actual.place!!.latitude, 1e-6)
                assertEquals(expected.place.longitude, actual.place.longitude, 1e-6)
            }
        }
    }

    @Test
    fun anEmptyListRoundTrips() {
        assertEquals(emptyList<LocationEntry>(), LocationCodec.decode(LocationCodec.encode(emptyList())))
    }

    @Test
    fun usesTwentyFiveBytesPerPhoto() {
        assertEquals(12 + entries.size * 25, LocationCodec.encode(entries).size)
    }

    @Test
    fun aDamagedFileDecodesToNull() {
        val good = LocationCodec.encode(entries)
        assertNotNull(LocationCodec.decode(good))
        assertNull(LocationCodec.decode(ByteArray(0)))
        assertNull(LocationCodec.decode(good.copyOf(good.size - 1))) // cut short
        assertNull(LocationCodec.decode(good + byteArrayOf(0))) // trailing bytes
        assertNull(LocationCodec.decode(good.also { it[0] = 0 }.copyOf())) // wrong magic
    }

    @Test
    fun aFileFromAnotherVersionDecodesToNull() {
        val bytes = LocationCodec.encode(entries)
        bytes[7] = 99
        assertNull(LocationCodec.decode(bytes))
    }

    @Test
    fun anImpossibleCountDecodesToNull() {
        val bytes = LocationCodec.encode(entries)
        bytes[8] = 0x7F // a count of billions, far more than the file holds
        assertNull(LocationCodec.decode(bytes))
    }
}
