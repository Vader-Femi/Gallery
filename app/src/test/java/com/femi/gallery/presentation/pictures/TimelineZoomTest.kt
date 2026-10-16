package com.femi.gallery.presentation.pictures

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineZoomTest {

    @Test
    fun zoomingInStopsAtTheMostDetailedLevel() {
        assertEquals(TimelineZoom.Day2, TimelineZoom.Day2.zoomedIn())
        assertEquals(TimelineZoom.Day2, TimelineZoom.Day3.zoomedIn())
    }

    @Test
    fun zoomingOutStopsAtTheCoarsestLevel() {
        assertEquals(TimelineZoom.Year7, TimelineZoom.Year7.zoomedOut())
        assertEquals(TimelineZoom.Year7, TimelineZoom.Month6.zoomedOut())
    }

    @Test
    fun levelsGetDenserAsYouZoomOut() {
        val columns = TimelineZoom.entries.map { it.columns }
        assertEquals(columns.sorted(), columns)
    }
}
