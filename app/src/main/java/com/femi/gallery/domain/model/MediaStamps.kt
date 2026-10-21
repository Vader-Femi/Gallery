package com.femi.gallery.domain.model

/** Ids of photos with the time each file was last modified, as parallel arrays. */
class MediaStamps(
    val ids: LongArray,
    val modified: LongArray,
) {
    val size: Int get() = ids.size

    companion object {
        val Empty = MediaStamps(LongArray(0), LongArray(0))
    }
}
