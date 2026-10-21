package com.femi.gallery.domain.geo

import com.femi.gallery.domain.model.MediaOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceIndexTest {

    private val index = PlaceIndex(
        listOf(
            LocationEntry(1, 10, GeoPoint(6.52, 3.38)), // Lagos
            LocationEntry(2, 10, null), // no place
            LocationEntry(3, 10, GeoPoint(9.08, 7.40)), // Abuja
            LocationEntry(4, 10, GeoPoint(51.5, -0.13)), // London
        ),
    )

    @Test
    fun countsAndLooksUpOnlyPhotosWithAPlace() {
        assertEquals(3, index.placedCount)
        assertEquals(GeoPoint(6.52, 3.38), index.placeOf(1))
        assertNull(index.placeOf(2))
        assertNull(index.placeOf(99))
    }

    @Test
    fun idsInFindsPhotosInsideABox() {
        assertEquals(setOf(1L, 3L), index.idsIn(GeoBox(south = 4.0, west = 2.0, north = 14.0, east = 15.0)))
        assertEquals(emptySet<Long>(), index.idsIn(GeoBox(south = -10.0, west = -10.0, north = -5.0, east = -5.0)))
    }

    @Test
    fun anEmptyIndexFindsNothing() {
        assertEquals(0, PlaceIndex.Empty.placedCount)
        assertEquals(emptySet<Long>(), PlaceIndex.Empty.idsIn(GeoBox(-90.0, -180.0, 90.0, 180.0)))
    }

    @Test
    fun locatedMediaKeepsTheOrderAndConvertsDatesToSeconds() {
        // Newest first; photo 2 has no place and 5 is unknown to the index.
        val order = MediaOrder(
            ids = longArrayOf(4, 5, 3, 2, 1),
            datesMillis = longArrayOf(9_000, 8_000, 7_000, 6_000, 5_000),
        )
        val located = LocatedMedia.from(order, index)
        assertEquals(listOf(4L, 3L, 1L), located.ids.toList())
        assertEquals(listOf(9L, 7L, 5L), located.dates.toList())
        assertEquals(51.5, located.latitudes[0], 1e-9)
        assertEquals(3.38, located.longitudes[2], 1e-9)
    }

    @Test
    fun locatedMediaOfNothingIsEmpty() {
        assertEquals(0, LocatedMedia.from(MediaOrder.Empty, index).size)
        assertEquals(0, LocatedMedia.from(MediaOrder(longArrayOf(1), longArrayOf(1)), PlaceIndex.Empty).size)
    }
}
