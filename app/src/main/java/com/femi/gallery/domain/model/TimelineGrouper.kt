package com.femi.gallery.domain.model

import java.time.Instant
import java.time.LocalDate
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

    /**
     * Merges day sections into months or years. Sections stay contiguous because the input is
     * ordered, so neighbours with the same period just have their counts added.
     */
    fun regroup(daySections: List<TimelineSection>, granularity: TimelineGranularity): List<TimelineSection> {
        if (granularity == TimelineGranularity.Day || daySections.isEmpty()) return daySections

        val result = ArrayList<TimelineSection>()
        for (section in daySections) {
            val date = LocalDate.ofEpochDay(section.epochDay)
            val periodStart = when (granularity) {
                TimelineGranularity.Month -> date.withDayOfMonth(1)
                TimelineGranularity.Year -> date.withDayOfYear(1)
                TimelineGranularity.Day -> date
            }.toEpochDay()

            val last = result.lastOrNull()
            if (last != null && last.epochDay == periodStart) {
                result[result.lastIndex] = last.copy(count = last.count + section.count)
            } else {
                result.add(TimelineSection(periodStart, section.startIndex, section.count))
            }
        }
        return result
    }
}
