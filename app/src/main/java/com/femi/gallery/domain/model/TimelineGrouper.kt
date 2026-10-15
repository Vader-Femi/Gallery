package com.femi.gallery.domain.model

import java.time.Instant
import java.time.ZoneId

object TimelineGrouper {

    /**
     * Groups a newest-first list of timestamps (epoch seconds) into per-day sections.
     * Day boundaries are cached so the 50k-photo case does one date conversion per day,
     * not per photo.
     */
    fun group(
        dateAddedSeconds: LongArray,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<TimelineSection> {
        if (dateAddedSeconds.isEmpty()) return emptyList()

        val sections = ArrayList<TimelineSection>()
        var currentDay = 0L
        var dayStart = 0L
        var dayEnd = 0L // exclusive
        var sectionStart = 0

        for (i in dateAddedSeconds.indices) {
            val seconds = dateAddedSeconds[i]
            if (i == 0 || seconds < dayStart || seconds >= dayEnd) {
                if (i > 0) sections.add(TimelineSection(currentDay, sectionStart, i - sectionStart))
                val date = Instant.ofEpochSecond(seconds).atZone(zone).toLocalDate()
                currentDay = date.toEpochDay()
                dayStart = date.atStartOfDay(zone).toEpochSecond()
                dayEnd = date.plusDays(1).atStartOfDay(zone).toEpochSecond()
                sectionStart = i
            }
        }
        sections.add(TimelineSection(currentDay, sectionStart, dateAddedSeconds.size - sectionStart))
        return sections
    }
}
