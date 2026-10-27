package com.femi.gallery.domain.model

/**
 * What the person changed about one story: its title and its cover photo. A story is found
 * automatically and has no id of its own, so an edit remembers the days it was made for
 * ([fromDay] to [toDay], see [Story]) and [StoryEdits] decides which story it belongs to later.
 */
data class StoryEdit(
    val fromDay: Long,
    val toDay: Long,
    /** The title chosen by the person; null keeps the one made from the dates. */
    val title: String? = null,
    /** MediaStore id of the cover photo; null keeps the automatic one. */
    val coverId: Long? = null,
) {
    val isEmpty: Boolean get() = title == null && coverId == null
}
