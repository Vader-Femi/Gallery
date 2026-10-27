package com.femi.gallery.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceNamesTest {
    @Test
    fun foldRemovesAccentsAndCase() {
        assertEquals("sao paulo", PlaceNames.fold("São Paulo"))
        assertEquals("zurich", PlaceNames.fold("Zürich"))
        assertEquals("malmo", PlaceNames.fold("Malmö"))
        assertEquals("strasse", PlaceNames.fold("Straße"))
    }

    @Test
    fun matchesWholeWordsAndWordStarts() {
        assertTrue(PlaceNames.matches("sao paulo", "paulo"))
        assertTrue(PlaceNames.matches("sao paulo", "pau"))
        assertTrue(PlaceNames.matches("new york", "york"))
        assertTrue(PlaceNames.matches("lagos", "lagos"))
    }

    @Test
    fun doesNotMatchInsideAWord() {
        assertFalse(PlaceNames.matches("urban", "ban"))
        assertFalse(PlaceNames.matches("hamburg", "burg"))
    }

    @Test
    fun shortWordsMustBeComplete() {
        assertFalse(PlaceNames.matches("paris", "pa"))
        assertTrue(PlaceNames.matches("al ain", "al"))
    }

    @Test
    fun emptyTermNeverMatches() {
        assertFalse(PlaceNames.matches("lagos", ""))
    }
}
