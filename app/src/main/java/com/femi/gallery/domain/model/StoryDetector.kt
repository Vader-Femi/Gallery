package com.femi.gallery.domain.model

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
 * A day counts as "active" with at least [MIN_PHOTOS_PER_DAY] photos. Consecutive active days
 * form a run (a trip, a long weekend), cut at [MAX_STORY_DAYS] days so a month of daily photos
 * does not become one endless story. A run is a story when it has [MIN_STORY_PHOTOS] photos or
 * more. Stories line up exactly with whole days, so a story's photos are simply everything
 * taken between its first and last day.
 */
object StoryDetector {
    const val MIN_PHOTOS_PER_DAY = 5
    const val MIN_STORY_PHOTOS = 12
    const val MAX_STORY_DAYS = 10

    fun detect(days: List<TimelineSection>): List<StoryCandidate> {
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
