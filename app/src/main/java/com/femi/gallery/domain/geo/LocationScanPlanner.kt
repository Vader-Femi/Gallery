package com.femi.gallery.domain.geo

/**
 * What the scan has to do: [keep] are the results that are still valid, and the photos in [scanIds]
 * (newest change first, so the map fills with recent photos first) still need their place read.
 * [scanModified] holds each of those photos' DATE_MODIFIED, to be stored with the result.
 */
class LocationScanPlan(
    val keep: List<LocationEntry>,
    val scanIds: LongArray,
    val scanModified: LongArray,
) {
    val toScan: Int get() = scanIds.size
}

object LocationScanPlanner {

    /**
     * Compares what was [cached] with the photos that exist now ([ids] and their [modified] times,
     * as parallel arrays). A result is kept when its photo is still there and unchanged; results of
     * deleted photos are dropped, and changed or new photos are scanned.
     */
    fun plan(cached: List<LocationEntry>, ids: LongArray, modified: LongArray): LocationScanPlan {
        require(ids.size == modified.size) { "Arrays must have the same size" }
        val cachedById = HashMap<Long, LocationEntry>(cached.size * 2)
        for (entry in cached) cachedById[entry.id] = entry

        val keep = ArrayList<LocationEntry>(ids.size)
        val todo = ArrayList<Int>()
        for (i in ids.indices) {
            val entry = cachedById[ids[i]]
            if (entry != null && entry.modified == modified[i]) keep.add(entry) else todo.add(i)
        }
        todo.sortByDescending { modified[it] }
        return LocationScanPlan(
            keep = keep,
            scanIds = LongArray(todo.size) { ids[todo[it]] },
            scanModified = LongArray(todo.size) { modified[todo[it]] },
        )
    }
}
