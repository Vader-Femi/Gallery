package com.femi.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StoryVideosTest {
    private val days = longArrayOf(10, 10, 12, 15, 15, 15, 20)

    @Test
    fun countsInsideTheRangeIncludingBothEnds() {
        assertEquals(2, StoryVideos.countInRange(days, 10, 10))
        assertEquals(6, StoryVideos.countInRange(days, 10, 15))
        assertEquals(7, StoryVideos.countInRange(days, 0, 100))
    }

    @Test
    fun noneOutside() {
        assertEquals(0, StoryVideos.countInRange(days, 21, 30))
        assertEquals(0, StoryVideos.countInRange(days, 0, 9))
        assertEquals(0, StoryVideos.countInRange(days, 16, 19))
    }

    @Test
    fun emptyAndBackwardsRanges() {
        assertEquals(0, StoryVideos.countInRange(LongArray(0), 0, 10))
        assertEquals(0, StoryVideos.countInRange(days, 15, 10))
    }
}
