package com.femi.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class TimelineGrouperTest {

    private val day = 86_400L

    @Test
    fun emptyInputGivesNoSections() {
        assertTrue(TimelineGrouper.group(LongArray(0), ZoneOffset.UTC).isEmpty())
    }

    @Test
    fun groupsConsecutiveItemsByDay() {
        val dates = longArrayOf(100 * day + 500, 100 * day + 10, 99 * day + 700, 99 * day + 1, 97 * day + 5)

        val sections = TimelineGrouper.group(dates, ZoneOffset.UTC)

        assertEquals(
            listOf(
                TimelineSection(epochDay = 100, startIndex = 0, count = 2),
                TimelineSection(epochDay = 99, startIndex = 2, count = 2),
                TimelineSection(epochDay = 97, startIndex = 4, count = 1),
            ),
            sections,
        )
    }

    @Test
    fun singleItemIsOneSection() {
        val sections = TimelineGrouper.group(longArrayOf(5 * day), ZoneOffset.UTC)
        assertEquals(listOf(TimelineSection(5, 0, 1)), sections)
    }
}
