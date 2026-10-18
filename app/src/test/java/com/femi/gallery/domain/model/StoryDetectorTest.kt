package com.femi.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryDetectorTest {

    /** Builds newest-first day sections from (epochDay to photo count) pairs. */
    private fun days(vararg days: Pair<Long, Int>): List<TimelineSection> {
        var start = 0
        return days.map { (day, count) ->
            TimelineSection(epochDay = day, startIndex = start, count = count).also { start += count }
        }
    }

    @Test
    fun aBusyDayIsAStory() {
        val stories = StoryDetector.detect(days(100L to 20))
        assertEquals(listOf(StoryCandidate(fromDay = 100, toDay = 100, count = 20, coverIndex = 10)), stories)
    }

    @Test
    fun aQuietDayIsNotAStory() {
        assertTrue(StoryDetector.detect(days(100L to 8)).isEmpty()) // fewer than 12 photos in total
        assertTrue(StoryDetector.detect(days(100L to 4)).isEmpty()) // not even an active day
    }

    @Test
    fun consecutiveBusyDaysFormOneStoryWithTheCoverInTheMiddle() {
        // Newest first: day 12 (6 photos), day 11 (7), day 10 (9) -> one story of 22 photos.
        val stories = StoryDetector.detect(days(12L to 6, 11L to 7, 10L to 9))
        assertEquals(1, stories.size)
        val story = stories.single()
        assertEquals(10L, story.fromDay)
        assertEquals(12L, story.toDay)
        assertEquals(22, story.count)
        assertEquals(11, story.coverIndex)
    }

    @Test
    fun aGapOrAQuietDaySplitsStories() {
        val stories = StoryDetector.detect(
            days(
                20L to 15, // story A
                19L to 3, // quiet day in between
                18L to 15, // story B
                16L to 15, // gap day 17 -> story C
            ),
        )
        assertEquals(listOf(20L to 20L, 18L to 18L, 16L to 16L), stories.map { it.fromDay to it.toDay })
    }

    @Test
    fun smallBusyDaysAddUpToAStory() {
        // Two active days with 6 photos each reach the 12-photo minimum together.
        val stories = StoryDetector.detect(days(51L to 6, 50L to 6))
        assertEquals(listOf(50L to 51L), stories.map { it.fromDay to it.toDay })
        assertEquals(12, stories.single().count)
    }

    @Test
    fun longRunsAreCutAtTheMaximumLength() {
        val run = (30L downTo 11L).map { it to 6 } // 20 consecutive busy days
        val stories = StoryDetector.detect(days(*run.toTypedArray()))
        assertEquals(2, stories.size)
        assertTrue(stories.all { it.toDay - it.fromDay + 1 <= StoryDetector.MAX_STORY_DAYS })
        assertEquals(StoryDetector.MAX_STORY_DAYS * 6, stories.first().count)
    }

    @Test
    fun emptyLibraryHasNoStories() {
        assertTrue(StoryDetector.detect(emptyList()).isEmpty())
    }
}
