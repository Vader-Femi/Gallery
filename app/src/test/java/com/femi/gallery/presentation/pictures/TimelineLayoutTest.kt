package com.femi.gallery.presentation.pictures

import com.femi.gallery.domain.model.TimelineSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineLayoutTest {

    // Grid: H0 m0 m1 | H1 m2 m3 | H2 m4  ->  indices 0..7
    private val layout = TimelineLayout(
        listOf(
            TimelineSection(epochDay = 100, startIndex = 0, count = 2),
            TimelineSection(epochDay = 99, startIndex = 2, count = 2),
            TimelineSection(epochDay = 97, startIndex = 4, count = 1),
        ),
    )

    @Test
    fun totalCountIncludesHeaders() {
        assertEquals(8, layout.totalCount)
    }

    @Test
    fun identifiesHeaders() {
        listOf(0, 3, 6).forEach { assertTrue("index $it", layout.isHeader(it)) }
        listOf(1, 2, 4, 5, 7).forEach { assertFalse("index $it", layout.isHeader(it)) }
    }

    @Test
    fun mapsGridIndexToMediaIndex() {
        assertEquals(0, layout.mediaIndexAt(1))
        assertEquals(1, layout.mediaIndexAt(2))
        assertEquals(2, layout.mediaIndexAt(4))
        assertEquals(3, layout.mediaIndexAt(5))
        assertEquals(4, layout.mediaIndexAt(7))
    }

    @Test
    fun mapsGridIndexToSection() {
        assertEquals(listOf(0, 0, 0, 1, 1, 1, 2, 2), (0 until 8).map(layout::sectionIndexAt))
    }

    @Test
    fun emptyLayoutHasNoItems() {
        val empty = TimelineLayout(emptyList())
        assertEquals(0, empty.totalCount)
        assertFalse(empty.isHeader(0))
    }

    @Test
    fun mapsMediaIndexToGridIndex() {
        // Grid: H0 m0 m1 | H1 m2 m3 | H2 m4  ->  media 0..4 sit at grid 1, 2, 4, 5, 7
        assertEquals(listOf(1, 2, 4, 5, 7), (0..4).map(layout::gridIndexForMedia))
    }

    @Test
    fun gridIndexAndMediaIndexAreInverses() {
        for (media in 0..4) {
            val grid = layout.gridIndexForMedia(media)
            assertFalse(layout.isHeader(grid))
            assertEquals(media, layout.mediaIndexAt(grid))
        }
    }
}
