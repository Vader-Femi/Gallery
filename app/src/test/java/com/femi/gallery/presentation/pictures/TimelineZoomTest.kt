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

    @Test
    fun compactWindowUsesDefaultColumns() {
        for (zoom in TimelineZoom.entries) {
            assertEquals(zoom.columns, responsiveTimelineColumns(zoom, screenWidthDp = 412))
            assertEquals(zoom.columns, responsiveTimelineColumns(zoom, screenWidthDp = 599))
        }
    }

    @Test
    fun mediumWindowScalesColumnsAppropriately() {
        assertEquals(3, responsiveTimelineColumns(TimelineZoom.Day2, screenWidthDp = 600))
        assertEquals(4, responsiveTimelineColumns(TimelineZoom.Day3, screenWidthDp = 700))
        assertEquals(5, responsiveTimelineColumns(TimelineZoom.Day4, screenWidthDp = 800))
        assertEquals(6, responsiveTimelineColumns(TimelineZoom.Day5, screenWidthDp = 839))
        assertEquals(8, responsiveTimelineColumns(TimelineZoom.Month6, screenWidthDp = 750))
        assertEquals(10, responsiveTimelineColumns(TimelineZoom.Year7, screenWidthDp = 750))
    }

    @Test
    fun expandedWindowScalesColumnsForLargeDisplays() {
        assertEquals(4, responsiveTimelineColumns(TimelineZoom.Day2, screenWidthDp = 840))
        assertEquals(5, responsiveTimelineColumns(TimelineZoom.Day3, screenWidthDp = 900))
        assertEquals(6, responsiveTimelineColumns(TimelineZoom.Day4, screenWidthDp = 1000))
        assertEquals(8, responsiveTimelineColumns(TimelineZoom.Day5, screenWidthDp = 1200))
        assertEquals(10, responsiveTimelineColumns(TimelineZoom.Month6, screenWidthDp = 1400))
        assertEquals(14, responsiveTimelineColumns(TimelineZoom.Year7, screenWidthDp = 1600))
    }

    @Test
    fun boundaryTransitionsAt600And840Dp() {
        // Compact to Medium boundary at 600dp
        assertEquals(2, responsiveTimelineColumns(TimelineZoom.Day2, screenWidthDp = 599))
        assertEquals(3, responsiveTimelineColumns(TimelineZoom.Day2, screenWidthDp = 600))

        // Medium to Expanded boundary at 840dp
        assertEquals(3, responsiveTimelineColumns(TimelineZoom.Day2, screenWidthDp = 839))
        assertEquals(4, responsiveTimelineColumns(TimelineZoom.Day2, screenWidthDp = 840))
    }
}
