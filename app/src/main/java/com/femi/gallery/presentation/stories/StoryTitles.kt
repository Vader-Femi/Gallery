package com.femi.gallery.presentation.stories

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Titles for stories, made from the days they cover. */
object StoryTitles {

    /**
     * `Sat, Oct 3` for one day, `Oct 3 – 5` or `Oct 30 – Nov 2` for several; the year is added
     * when it isn't the current one. "On this day in YYYY" for past anniversaries.
     */
    fun range(
        fromDay: Long,
        toDay: Long,
        today: LocalDate = LocalDate.now(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val from = LocalDate.ofEpochDay(minOf(fromDay, toDay))
        val to = LocalDate.ofEpochDay(maxOf(fromDay, toDay))
        val weekdayAndDay = DateTimeFormatter.ofPattern("EEE, MMM d", locale)
        val monthAndDay = DateTimeFormatter.ofPattern("MMM d", locale)
        val dayOnly = DateTimeFormatter.ofPattern("d", locale)
        val withYear = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)

        val isPastOnThisDay = from == to && from.month == today.month && from.dayOfMonth == today.dayOfMonth && from.year < today.year
        if (isPastOnThisDay) return "On this day in ${from.year}"

        return when {
            from == to && from.year == today.year -> from.format(weekdayAndDay)
            from == to -> from.format(withYear)
            from.year != to.year -> "${from.format(withYear)} – ${to.format(withYear)}"
            else -> {
                val end = if (from.month == to.month) to.format(dayOnly) else to.format(monthAndDay)
                val text = "${from.format(monthAndDay)} – $end"
                if (from.year == today.year) text else "$text, ${from.year}"
            }
        }
    }
}
