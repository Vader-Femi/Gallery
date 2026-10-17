package com.femi.gallery.domain.model

/** Item ids with their dates, in display order (newest first). */
class MediaOrder(
    val ids: LongArray,
    val datesMillis: LongArray,
) {
    val size: Int get() = ids.size

    companion object {
        val Empty = MediaOrder(LongArray(0), LongArray(0))
    }
}

object MediaOrdering {

    /**
     * The date a photo is shown under: when it was taken, or when it was added to the phone if
     * the file has no "date taken" (WhatsApp images, screenshots, downloads).
     */
    fun effectiveDateMillis(taken: Long, addedSeconds: Long): Long =
        if (taken > 0) taken else addedSeconds * 1000L

    /** Sorts the first [count] items newest first; ties go to the higher id so the order is stable. */
    fun newestFirst(ids: LongArray, datesMillis: LongArray, count: Int = ids.size): MediaOrder {
        val order = (0 until count).sortedWith(
            compareByDescending<Int> { datesMillis[it] }.thenByDescending { ids[it] },
        )
        return MediaOrder(
            ids = LongArray(count) { ids[order[it]] },
            datesMillis = LongArray(count) { datesMillis[order[it]] },
        )
    }
}
