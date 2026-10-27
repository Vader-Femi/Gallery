package com.femi.gallery.domain.model

/**
 * An automatically found event: a day, or a few days in a row, with lots of photos.
 * [fromDay] and [toDay] are local calendar days (`LocalDate.toEpochDay()`), both included.
 */
data class Story(
    val fromDay: Long,
    val toDay: Long,
    val count: Int,
    /** content:// URI of a photo from the middle of the story. */
    val coverUri: String,
    /** The title the person chose, or null to show the dates. */
    val title: String? = null,
    /** How many of the [count] items are videos. */
    val videoCount: Int = 0,
)
