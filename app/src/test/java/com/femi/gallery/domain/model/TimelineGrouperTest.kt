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

    @Test
    fun regroupByMonthMergesNeighbouringDays() {
        // 2026-10-15, 2026-10-03, 2026-09-30, 2026-09-01, 2025-12-31
        val days = listOf(
            TimelineSection(java.time.LocalDate.of(2026, 10, 15).toEpochDay(), 0, 3),
            TimelineSection(java.time.LocalDate.of(2026, 10, 3).toEpochDay(), 3, 2),
            TimelineSection(java.time.LocalDate.of(2026, 9, 30).toEpochDay(), 5, 4),
            TimelineSection(java.time.LocalDate.of(2026, 9, 1).toEpochDay(), 9, 1),
            TimelineSection(java.time.LocalDate.of(2025, 12, 31).toEpochDay(), 10, 6),
        )

        val months = TimelineGrouper.regroup(days, TimelineGranularity.Month)

        assertEquals(
            listOf(
                TimelineSection(java.time.LocalDate.of(2026, 10, 1).toEpochDay(), 0, 5),
                TimelineSection(java.time.LocalDate.of(2026, 9, 1).toEpochDay(), 5, 5),
                TimelineSection(java.time.LocalDate.of(2025, 12, 1).toEpochDay(), 10, 6),
            ),
            months,
        )
    }

    @Test
    fun regroupByYearAndDayUnchanged() {
        val days = listOf(
            TimelineSection(java.time.LocalDate.of(2026, 10, 15).toEpochDay(), 0, 3),
            TimelineSection(java.time.LocalDate.of(2026, 1, 2).toEpochDay(), 3, 2),
            TimelineSection(java.time.LocalDate.of(2025, 6, 1).toEpochDay(), 5, 1),
        )

        val years = TimelineGrouper.regroup(days, TimelineGranularity.Year)

        assertEquals(
            listOf(
                TimelineSection(java.time.LocalDate.of(2026, 1, 1).toEpochDay(), 0, 5),
                TimelineSection(java.time.LocalDate.of(2025, 1, 1).toEpochDay(), 5, 1),
            ),
            years,
        )
        assertEquals(days, TimelineGrouper.regroup(days, TimelineGranularity.Day))
    }
}
