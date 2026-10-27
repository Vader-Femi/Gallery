package com.femi.gallery.domain.model

/** How many videos each story holds. */
object StoryVideos {

    /**
     * The number of values in [sortedDays] (calendar days as epoch days, ascending) that fall in
     * [fromDay]..[toDay], both included.
     */
    fun countInRange(sortedDays: LongArray, fromDay: Long, toDay: Long): Int {
        if (toDay < fromDay) return 0
        return firstIndexAfter(sortedDays, toDay) - firstIndexFrom(sortedDays, fromDay)
    }

    /** Index of the first value that is >= [day] (the size when there is none). */
    private fun firstIndexFrom(values: LongArray, day: Long): Int {
        var low = 0
        var high = values.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (values[mid] < day) low = mid + 1 else high = mid
        }
        return low
    }

    /** Index of the first value that is > [day]. */
    private fun firstIndexAfter(values: LongArray, day: Long): Int = firstIndexFrom(values, day + 1)
}
