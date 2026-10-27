package com.femi.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StoryDetectorTest {

    /** Pinned, so these tests do not depend on the day they run (their days are in 1970). */
    private val today = LocalDate.of(2026, 10, 27)

    private fun detect(days: List<TimelineSection>, on: LocalDate = today) = StoryDetector.detect(days, on)

    private fun day(year: Int, month: Int, dayOfMonth: Int) = LocalDate.of(year, month, dayOfMonth).toEpochDay()

    /** Builds newest-first day sections from (epochDay to photo count) pairs. */
    private fun days(vararg days: Pair<Long, Int>): List<TimelineSection> {
        var start = 0
        return days.map { (day, count) ->
            TimelineSection(epochDay = day, startIndex = start, count = count).also { start += count }
        }
    }

    @Test
    fun aBusyDayIsAStory() {
        val stories = detect(days(100L to 20))
        assertEquals(listOf(StoryCandidate(fromDay = 100, toDay = 100, count = 20, coverIndex = 10)), stories)
    }

    @Test
    fun aQuietDayIsNotAStory() {
        assertTrue(detect(days(100L to 8)).isEmpty()) // fewer than 12 photos in total
        assertTrue(detect(days(100L to 4)).isEmpty()) // not even an active day
    }

    @Test
    fun consecutiveBusyDaysFormOneStoryWithTheCoverInTheMiddle() {
        // Newest first: day 12 (6 photos), day 11 (7), day 10 (9) -> one story of 22 photos.
        val stories = detect(days(12L to 6, 11L to 7, 10L to 9))
        assertEquals(1, stories.size)
        val story = stories.single()
        assertEquals(10L, story.fromDay)
        assertEquals(12L, story.toDay)
        assertEquals(22, story.count)
        assertEquals(11, story.coverIndex)
    }

    @Test
    fun aGapOrAQuietDaySplitsStories() {
        val stories = detect(
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
        val stories = detect(days(51L to 6, 50L to 6))
        assertEquals(listOf(50L to 51L), stories.map { it.fromDay to it.toDay })
        assertEquals(12, stories.single().count)
    }

    @Test
    fun longRunsAreCutAtTheMaximumLength() {
        val run = (30L downTo 11L).map { it to 6 } // 20 consecutive busy days
        val stories = detect(days(*run.toTypedArray()))
        assertEquals(2, stories.size)
        assertTrue(stories.all { it.toDay - it.fromDay + 1 <= StoryDetector.MAX_STORY_DAYS })
        assertEquals(StoryDetector.MAX_STORY_DAYS * 6, stories.first().count)
    }

    @Test
    fun emptyLibraryHasNoStories() {
        assertTrue(detect(emptyList()).isEmpty())
    }

    @Test
    fun aPastYearsSameDayIsAMemory() {
        val memory = day(2024, 10, 27)
        val stories = detect(days(memory to 4), today)
        assertEquals(listOf(StoryCandidate(fromDay = memory, toDay = memory, count = 4, coverIndex = 0)), stories)
    }

    @Test
    fun aMemoryNeedsAFewPhotos() {
        assertTrue(detect(days(day(2024, 10, 27) to 2)).isEmpty())
    }

    @Test
    fun thisYearsDayIsNotAMemory() {
        assertTrue(detect(days(day(2026, 10, 27) to 4)).isEmpty())
    }

    @Test
    fun anotherDayIsNotAMemory() {
        assertTrue(detect(days(day(2024, 10, 26) to 4)).isEmpty())
    }

    @Test
    fun aBusyAnniversaryIsNotOfferedTwice() {
        val busy = day(2024, 10, 27)
        val stories = detect(days(busy to 20))
        assertEquals(1, stories.size)
        assertEquals(20, stories.single().count)
    }

    @Test
    fun aMemoryInsideALongerTripIsStillOffered() {
        val d = day(2024, 10, 27)
        val stories = detect(days(d + 1 to 6, d to 6, d - 1 to 6)) // a three-day trip around the anniversary
        assertEquals(2, stories.size)
        assertEquals(listOf(d, d - 1), stories.map { it.fromDay }) // the memory first, then the trip (from d - 1)
        assertEquals(d - 1, stories[1].fromDay)
    }

    @Test
    fun memoriesComeBeforeTrips() {
        val memory = day(2023, 10, 27)
        val trip = day(2026, 6, 1)
        val stories = detect(days(trip to 20, memory to 5))
        assertEquals(memory, stories.first().fromDay)
        assertEquals(trip, stories.last().fromDay)
    }

    @Test
    fun aLeapDayIsRememberedOnFeb28InOtherYears() {
        val leapDay = LocalDate.of(2024, 2, 29)
        assertTrue(StoryDetector.isAnniversary(leapDay, LocalDate.of(2027, 2, 28)))
        assertTrue(!StoryDetector.isAnniversary(leapDay, LocalDate.of(2027, 3, 1)))
        assertTrue(!StoryDetector.isAnniversary(leapDay, LocalDate.of(2028, 2, 28))) // 2028 has its own Feb 29
        assertTrue(StoryDetector.isAnniversary(leapDay, LocalDate.of(2028, 2, 29)))
    }
}
