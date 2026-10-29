package com.femi.gallery.domain.duplicates

import com.femi.gallery.domain.model.MediaItem

/**
 * Files with exactly the same content. One of them ([keepId]) stays; the others are the extra copies
 * that can be moved to the recycle bin.
 */
data class DuplicateGroup(
    val items: List<MediaItem>,
    val keepId: Long,
) {
    /** The copies that would be removed. */
    val extraCopies: List<MediaItem> get() = items.filter { it.id != keepId }

    /** Space freed by removing [extraCopies]. */
    val reclaimableBytes: Long get() = extraCopies.sumOf { it.sizeBytes }

    /** The same group with [id] as the file to keep; unchanged when [id] is not in the group. */
    fun withKeep(id: Long): DuplicateGroup = if (items.any { it.id == id }) copy(keepId = id) else this
}

/** What the duplicate search is busy with, and how far it has got within that step. */
data class DuplicateProgress(
    val phase: DuplicatePhase,
    val done: Int,
    val total: Int,
)

enum class DuplicatePhase {
    /** Reading a few small pieces of every file that has the same size as another. */
    Sampling,

    /** Reading the whole of files whose pieces matched, to be sure. */
    Verifying,
}
