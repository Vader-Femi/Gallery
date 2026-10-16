package com.femi.gallery.domain.model

/**
 * A run of consecutive media items that share a day, month or year, depending on the
 * [TimelineGranularity] it was built for. [epochDay] is the first day of that period.
 */
data class TimelineSection(
    val epochDay: Long,
    /** Index of the section's first item in the full, newest-first media list. */
    val startIndex: Int,
    val count: Int,
)

/** How coarsely the timeline is grouped. */
enum class TimelineGranularity { Day, Month, Year }
