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
    /** An auto-generated smart title based on place, people, scene, or anniversary. */
    val smartTitle: String? = null,
    /** A secondary contextual string like "Oct 3 – 5, 2025" or "With Alice • 12 photos". */
    val subtitle: String? = null,
    /** The story category: Trip, People, Scene, Anniversary, or Outing. */
    val theme: StoryTheme = StoryTheme.Outing,
) {
    /** The headline to show: user's custom title first, then smart auto title, else null. */
    val displayTitle: String?
        get() = title?.takeIf { it.isNotBlank() } ?: smartTitle?.takeIf { it.isNotBlank() }
}

enum class StoryTheme {
    Trip,
    People,
    Scene,
    Anniversary,
    Outing,
}
