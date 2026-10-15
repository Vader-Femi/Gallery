package com.femi.gallery.presentation.pictures

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
}
