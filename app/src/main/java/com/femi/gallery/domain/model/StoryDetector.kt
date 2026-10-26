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

    fun detect(days: List<TimelineSection>, today: LocalDate = LocalDate.now()): List<StoryCandidate> {
        val stories = ArrayList<StoryCandidate>()

        // 1. "On this day" memories in past years (min 3 photos)
        val todayMonth = today.monthValue
        val todayDay = today.dayOfMonth
        for (day in days) {
            val date = LocalDate.ofEpochDay(day.epochDay)
            if (date.monthValue == todayMonth && date.dayOfMonth == todayDay && date.year < today.year && day.count >= 3) {
                stories.add(
                    StoryCandidate(
                        fromDay = day.epochDay,
                        toDay = day.epochDay,
                        count = day.count,
                        coverIndex = day.startIndex,
                    ),
                )
            }
        }

        // 2. Regular trip/event stories
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
