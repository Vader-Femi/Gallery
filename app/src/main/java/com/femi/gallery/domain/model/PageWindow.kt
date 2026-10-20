package com.femi.gallery.domain.model

/**
 * The slice `[start, end)` of an ordered list that one page load covers, and the keys that chain
 * pages together.
 *
 * Page keys are positions. Loading forward starts at the key and takes `loadSize` items. Loading
 * backward (a "prepend") gets the position the new page must END at, so it fits exactly against the
 * page already loaded, whatever size that earlier page had. Computing the previous key as
 * `start - loadSize` instead is wrong: the first page after a jump is loaded with a larger size than
 * later pages, so the page that follows would land in the wrong place and every photo in it would
 * show up under another position.
 */
data class PageWindow(val start: Int, val end: Int) {

    /** Key to pass to load the page before this one (it ends where this one starts), or null. */
    val prevKey: Int? get() = if (start == 0) null else start

    /** Key to load the page after this one, or null at the end of a list of [total] items. */
    fun nextKey(total: Int): Int? = if (end >= total) null else end

    companion object {
        /** A refresh or an append: [loadSize] items from position [key] (the start when null). */
        fun forward(key: Int?, loadSize: Int, total: Int): PageWindow {
            val start = (key ?: 0).coerceIn(0, maxOf(0, total - 1))
            return PageWindow(start, minOf(total, start + loadSize))
        }

        /** A prepend: up to [loadSize] items that end at position [endKey]. */
        fun backward(endKey: Int, loadSize: Int, total: Int): PageWindow {
            val end = endKey.coerceIn(0, total)
            return PageWindow(maxOf(0, end - loadSize), end)
        }
    }
}
