package com.femi.gallery.presentation.stories

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class StoryTitlesTest {

    private val today = LocalDate.of(2026, 10, 18)

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    private fun title(from: Long, to: Long) = StoryTitles.range(from, to, today, Locale.US)

    @Test
    fun singleDayThisYear() {
        assertEquals("Sat, Oct 3", title(day(2026, 10, 3), day(2026, 10, 3)))
    }

    @Test
    fun singleDayInAnotherYearShowsTheYear() {
        assertEquals("Dec 31, 2025", title(day(2025, 12, 31), day(2025, 12, 31)))
    }

    @Test
    fun severalDaysInOneMonth() {
        assertEquals("Oct 3 – 5", title(day(2026, 10, 3), day(2026, 10, 5)))
    }

    @Test
    fun severalDaysAcrossMonths() {
        assertEquals("Oct 30 – Nov 2", title(day(2026, 10, 30), day(2026, 11, 2)))
    }

    @Test
    fun previousYearGetsAYearSuffix() {
        assertEquals("Mar 3 – 5, 2025", title(day(2025, 3, 3), day(2025, 3, 5)))
    }

    @Test
    fun acrossNewYear() {
        assertEquals("Dec 30, 2025 – Jan 2, 2026", title(day(2025, 12, 30), day(2026, 1, 2)))
    }
}
