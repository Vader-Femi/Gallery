package com.femi.gallery.domain.text

/**
 * What a background scan has to do: [keepIds] are the photos whose earlier result is still valid, and
 * the photos in [scanIds] (most recently changed first, so recent photos are ready first) still need
 * scanning. [scanModified] holds each of those photos' DATE_MODIFIED, to be stored with the result.
 */
class StampPlan(
    val keepIds: Set<Long>,
    val scanIds: LongArray,
    val scanModified: LongArray,
) {
    val toScan: Int get() = scanIds.size
}

object StampPlanner {

    /**
     * Compares what was scanned before ([cachedIds] and their [cachedModified] times) with the photos
     * that exist now ([ids] and [modified]). A result is kept when its photo is still there and
     * unchanged; changed and new photos are scanned; results of deleted photos are not kept.
     */
    fun plan(
        cachedIds: LongArray,
        cachedModified: LongArray,
        ids: LongArray,
        modified: LongArray,
    ): StampPlan {
        require(cachedIds.size == cachedModified.size && ids.size == modified.size) {
            "Arrays must have the same size"
        }
        val cached = HashMap<Long, Long>(cachedIds.size * 2)
        for (i in cachedIds.indices) cached[cachedIds[i]] = cachedModified[i]

        val keep = HashSet<Long>()
        val todo = ArrayList<Int>()
        for (i in ids.indices) {
            if (cached[ids[i]] == modified[i]) keep.add(ids[i]) else todo.add(i)
        }
        todo.sortByDescending { modified[it] }
        return StampPlan(
            keepIds = keep,
            scanIds = LongArray(todo.size) { ids[todo[it]] },
            scanModified = LongArray(todo.size) { modified[todo[it]] },
        )
    }
}
