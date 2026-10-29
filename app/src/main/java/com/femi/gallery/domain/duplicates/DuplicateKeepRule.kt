package com.femi.gallery.domain.duplicates

import com.femi.gallery.domain.model.MediaItem

/** Which copy of a duplicate is the one worth keeping, when the person has not chosen. */
object DuplicateKeepRule {

    /**
     * In order: a favorite (removing it would lose the mark), a file in the camera's own folder under
     * DCIM (the original rather than something saved or shared from it), the one taken first, the shorter
     * name (copies are usually named "...(1)" or "... copy"), and finally the lowest id so the answer is stable.
     */
    fun choose(items: List<MediaItem>): MediaItem = items.minWithOrNull(
        compareByDescending<MediaItem> { it.isFavorite }
            .thenByDescending { isOriginalFolder(it) }
            .thenBy { it.dateTakenMillis }
            .thenBy { it.displayName.length }
            .thenBy { it.id },
    ) ?: throw IllegalArgumentException("A duplicate group needs at least one file")

    private fun isOriginalFolder(item: MediaItem): Boolean = item.relativePath.startsWith("DCIM/", ignoreCase = true)
}
