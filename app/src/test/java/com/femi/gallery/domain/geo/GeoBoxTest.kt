package com.femi.gallery.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoBoxTest {

    private val lagos = GeoBox(south = 6.3, west = 3.1, north = 6.7, east = 3.7)

    @Test
    fun containsPointsInsideAndOnTheEdges() {
        assertTrue(lagos.contains(6.5, 3.4))
        assertTrue(lagos.contains(6.3, 3.1))
        assertTrue(lagos.contains(6.7, 3.7))
        assertFalse(lagos.contains(6.8, 3.4))
        assertFalse(lagos.contains(6.5, 3.8))
    }

    @Test
    fun aBoxCanCrossTheAntimeridian() {
        val fiji = GeoBox(south = -20.0, west = 175.0, north = -15.0, east = -178.0)
        assertTrue(fiji.crossesAntimeridian)
        assertTrue(fiji.contains(-17.0, 179.0))
        assertTrue(fiji.contains(-17.0, -179.0))
        assertFalse(fiji.contains(-17.0, 0.0))
        assertFalse(lagos.crossesAntimeridian)
    }

    @Test
    fun roundTripsThroughAQuery() {
        val query = lagos.toQuery()
        assertEquals("in:6.300000,3.100000,6.700000,3.700000", query)
        assertEquals(lagos, GeoBox.parse(query.removePrefix("in:")))
    }

    @Test
    fun theQueryRoundsOutwardsSoEdgePointsStayInside() {
        val box = GeoBox(south = 6.1234567, west = 3.7654321, north = 6.9876543, east = 3.9999999)
        val parsed = GeoBox.parse(box.toQuery().removePrefix("in:"))!!
        assertTrue(parsed.contains(6.1234567, 3.7654321))
        assertTrue(parsed.contains(6.9876543, 3.9999999))
    }

    @Test
    fun theQueryUsesADotWhateverTheLocale() {
        val previous = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        try {
            assertEquals("in:6.300000,3.100000,6.700000,3.700000", lagos.toQuery())
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun rejectsBoxesThatAreNotValid() {
        assertNull(GeoBox.parse(""))
        assertNull(GeoBox.parse("1,2,3"))
        assertNull(GeoBox.parse("a,b,c,d"))
        assertNull(GeoBox.parse("7,3,6,4")) // south above north
        assertNull(GeoBox.parse("-95,3,6,4"))
        assertNull(GeoBox.parse("1,200,6,4"))
        assertNull(GeoBox.parse("NaN,3,6,4"))
    }
}
