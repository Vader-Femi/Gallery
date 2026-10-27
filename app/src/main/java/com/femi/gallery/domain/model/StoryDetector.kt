package com.femi.gallery.domain.model

import java.time.LocalDate

/** A story before its cover is loaded; [coverIndex] points into the newest-first media order. */
data class StoryCandidate(
    val fromDay: Long,
    val toDay: Long,
    val count: Int,
    val coverIndex: Int,
)

/**
 * Finds stories in the day sections of the library (newest first).
 *
 * Includes "On this day" memories from past years, and trip/event stories (busy runs of days).
 */
object StoryDetector {
    const val MIN_PHOTOS_PER_DAY = 5
    const val MIN_STORY_PHOTOS = 12
    const val MAX_STORY_DAYS = 10

    /** Fewest photos a past year's day needs to be offered as a memory. */
    const val MIN_ON_THIS_DAY_PHOTOS = 3

    /**
     * Whether [date] is the same calendar day as [today] in an earlier year. A leap day (Feb 29) is
     * remembered on Feb 28 in years that have no Feb 29.
     */
    fun isAnniversary(date: LocalDate, today: LocalDate): Boolean {
        if (date.year >= today.year) return false
        if (date.monthValue == today.monthValue && date.dayOfMonth == today.dayOfMonth) return true
        return date.monthValue == 2 && date.dayOfMonth == 29 &&
            today.monthValue == 2 && today.dayOfMonth == 28 && !today.isLeapYear
    }

    fun detect(days: List<TimelineSection>, today: LocalDate = LocalDate.now()): List<StoryCandidate> {
        val trips = detectTrips(days)
        // A busy single day that is already a story is not offered a second time as a memory.
        val tripKeys = trips.mapTo(HashSet()) { it.fromDay to it.toDay }
        val memories = days
            .filter { it.count >= MIN_ON_THIS_DAY_PHOTOS && isAnniversary(LocalDate.ofEpochDay(it.epochDay), today) }
            .filter { (it.epochDay to it.epochDay) !in tripKeys }
            .map { StoryCandidate(fromDay = it.epochDay, toDay = it.epochDay, count = it.count, coverIndex = it.startIndex) }
        return memories + trips
    }

    private fun detectTrips(days: List<TimelineSection>): List<StoryCandidate> {
        val stories = ArrayList<StoryCandidate>()
        var run = ArrayList<TimelineSection>()

        fun finishRun() {
            if (run.isNotEmpty()) {
                val total = run.sumOf { it.count }
                if (total >= MIN_STORY_PHOTOS) {
                    val newest = run.first()
                    val oldest = run.last()
                    stories.add(
                        StoryCandidate(
                            fromDay = oldest.epochDay,
                            toDay = newest.epochDay,
                            count = total,
                            coverIndex = newest.startIndex + total / 2,
                        ),
                    )
                }
            }
            run = ArrayList()
        }

        for (day in days) {
            if (day.count < MIN_PHOTOS_PER_DAY) {
                finishRun()
                continue
            }
            val previous = run.lastOrNull()
            val continuesRun = previous == null || previous.epochDay - day.epochDay == 1L
            if (!continuesRun || run.size >= MAX_STORY_DAYS) finishRun()
            run.add(day)
        }
        finishRun()
        return stories
    }
}
