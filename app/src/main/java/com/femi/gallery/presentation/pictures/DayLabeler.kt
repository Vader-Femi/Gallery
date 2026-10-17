package com.femi.gallery.presentation.pictures

import com.femi.gallery.domain.model.TimelineGranularity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

import androidx.collection.LongSparseArray
import androidx.collection.getOrElse
import androidx.collection.set

class DayLabeler(
    private val todayLabel: String,
    private val yesterdayLabel: String,
    locale: Locale = Locale.getDefault(),
    private val today: LocalDate = LocalDate.now(),
) {
    private val sameYear = DateTimeFormatter.ofPattern("EEE, MMM d", locale)
    private val otherYear = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
    
    private val monthSameYear = DateTimeFormatter.ofPattern("MMMM", locale)
    private val monthOtherYear = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    
    private val year = DateTimeFormatter.ofPattern("yyyy", locale)

    private val dayCache = LongSparseArray<String>()
    private val monthCache = LongSparseArray<String>()
    private val yearCache = LongSparseArray<String>()

    fun dayLabel(epochDay: Long): String = dayCache.getOrElse(epochDay) {
        val date = LocalDate.ofEpochDay(epochDay)
        val result = when {
            date == today -> todayLabel
            date == today.minusDays(1) -> yesterdayLabel
            date.year == today.year -> date.format(sameYear)
            else -> date.format(otherYear)
        }
        dayCache[epochDay] = result
        result
    }

    fun monthLabel(epochDay: Long): String = monthCache.getOrElse(epochDay) {
        val date = LocalDate.ofEpochDay(epochDay)
        val result = if (date.year == today.year) {
            date.format(monthSameYear)
        } else {
            date.format(monthOtherYear)
        }
        monthCache[epochDay] = result
        result
    }

    fun yearLabel(epochDay: Long): String = yearCache.getOrElse(epochDay) {
        val result = LocalDate.ofEpochDay(epochDay).format(year)
        yearCache[epochDay] = result
        result
    }

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
