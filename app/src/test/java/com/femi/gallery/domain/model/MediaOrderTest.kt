package com.femi.gallery.domain.model

import com.femi.gallery.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaOrderTest {

    private val order = MediaOrder(
        ids = longArrayOf(9, 7, 5, 3, 1),
        datesMillis = longArrayOf(90, 70, 50, 30, 10),
    )

    @Test
    fun retainIdsKeepsTheOrderAndTheDates() {
        val kept = order.retainIds(setOf(7L, 3L, 1L, 42L))
        assertEquals(listOf(7L, 3L, 1L), kept.ids.toList())
        assertEquals(listOf(70L, 30L, 10L), kept.datesMillis.toList())
    }

    @Test
    fun retainingNothingGivesAnEmptyOrder() {
        assertEquals(0, order.retainIds(emptySet()).size)
        assertEquals(0, MediaOrder.Empty.retainIds(setOf(1L)).size)
    }

    @Test
    fun retainingEverythingChangesNothing() {
        val kept = order.retainIds(order.ids.toSet())
        assertEquals(order.ids.toList(), kept.ids.toList())
    }

    @Test
    fun anUnsetExifPlaceIsNotAPlace() {
        assertNull(GeoPoint.fromExif(0.0, 0.0))
        assertNull(GeoPoint.fromExif(91.0, 10.0))
        assertNull(GeoPoint.fromExif(10.0, 181.0))
        assertNull(GeoPoint.fromExif(Double.NaN, 3.0))
        assertEquals(GeoPoint(0.0, 3.4), GeoPoint.fromExif(0.0, 3.4)) // the equator is a real place
        assertEquals(GeoPoint(6.5, 3.4), GeoPoint.fromExif(6.5, 3.4))
    }
}
