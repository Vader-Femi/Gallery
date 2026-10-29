package com.femi.gallery.domain.store

/**
 * What has to be written to a database so it holds exactly [RowDiff.between]'s `now` list, given what it
 * already holds. The scans hand over their whole list at every checkpoint; only the rows that really
 * changed (new, or different) are written, and rows that are no longer in the list are deleted.
 */
data class RowChanges<T>(
    val changed: List<T>,
    val removedIds: List<Long>,
) {
    val isEmpty: Boolean get() = changed.isEmpty() && removedIds.isEmpty()
}

object RowDiff {

    /** The changes that turn the rows in [saved] (by id) into the rows of [now]. A later row with the same id wins. */
    fun <T> between(saved: Map<Long, T>, now: List<T>, idOf: (T) -> Long): RowChanges<T> {
        val latest = LinkedHashMap<Long, T>(now.size)
        for (row in now) latest[idOf(row)] = row
        val changed = latest.values.filter { saved[idOf(it)] != it }
        val removed = saved.keys.filter { it !in latest }
        return RowChanges(changed, removed)
    }
}
