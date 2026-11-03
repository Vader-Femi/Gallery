package com.femi.gallery.domain.similar

import com.femi.gallery.domain.model.MediaItem

/** Which photo of a group of similar ones is the one worth keeping, when the person has not chosen. */
object SimilarKeepRule {
    /**
     * In order: a favorite, the most pixels, the biggest file (less compressed), a file in the camera's own folder
     * under DCIM, the one taken first, and finally the lowest id so that the answer is stable.
     */
    fun choose(items: List<MediaItem>): MediaItem = items.minWithOrNull(
        compareByDescending<MediaItem> { it.isFavorite }
            .thenByDescending { it.width.toLong() * it.height }
            .thenByDescending { it.sizeBytes }
            .thenByDescending { it.relativePath.startsWith("DCIM/", ignoreCase = true) }
            .thenBy { it.dateTakenMillis }
            .thenBy { it.id },
    ) ?: throw IllegalArgumentException("A group needs at least one photo")
}
