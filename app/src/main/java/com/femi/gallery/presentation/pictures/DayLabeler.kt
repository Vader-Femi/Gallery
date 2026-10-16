package com.femi.gallery.presentation.pictures

import com.femi.gallery.domain.model.TimelineGranularity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DayLabeler(
    private val todayLabel: String,
    private val yesterdayLabel: String,
    locale: Locale = Locale.getDefault(),
    private val today: LocalDate = LocalDate.now(),
) {
    private val sameYear = DateTimeFormatter.ofPattern("EEE, MMM d", locale)
    private val otherYear = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
    private val month = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    private val year = DateTimeFormatter.ofPattern("yyyy", locale)

    fun dayLabel(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return when {
            date == today -> todayLabel
            date == today.minusDays(1) -> yesterdayLabel
            date.year == today.year -> date.format(sameYear)
            else -> date.format(otherYear)
        }
    }

    fun monthLabel(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(month)

    fun yearLabel(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(year)

    /** Section header: "Today", "October 2026" or "2025", depending on the zoom level. */
    fun headerLabel(epochDay: Long, granularity: TimelineGranularity): String = when (granularity) {
        TimelineGranularity.Day -> dayLabel(epochDay)
        TimelineGranularity.Month -> monthLabel(epochDay)
        TimelineGranularity.Year -> yearLabel(epochDay)
    }

    /** What the fast-scroll bubble shows: a month, or a year when zoomed all the way out. */
    fun scrollLabel(epochDay: Long, granularity: TimelineGranularity): String =
        if (granularity == TimelineGranularity.Year) yearLabel(epochDay) else monthLabel(epochDay)
}
