package com.femi.gallery.presentation.pictures

import com.femi.gallery.domain.model.TimelineSection

/**
 * Maps positions in the grid (day headers + photos interleaved) to sections and media indices.
 *
 * Grid order is: header(0), photos of section 0, header(1), photos of section 1, ...
 * so the header of section k sits at `section.startIndex + k`.
 */
class TimelineLayout(val sections: List<TimelineSection>) {

    private val headerIndices = IntArray(sections.size) { sections[it].startIndex + it }

    val totalCount: Int =
        (sections.lastOrNull()?.let { it.startIndex + it.count } ?: 0) + sections.size

    private var cachedGridIndex = -1
    private var cachedIsHeader = false
    private var cachedSectionIndex = -1

    private fun lookup(gridIndex: Int) {
        if (cachedGridIndex == gridIndex) return
        val result = headerIndices.binarySearch(gridIndex)
        cachedIsHeader = result >= 0
        cachedSectionIndex = if (result >= 0) result else -result - 2
        cachedGridIndex = gridIndex
    }

    /** Index of the section that owns [gridIndex] (as header or photo). Requires non-empty sections. */
    fun sectionIndexAt(gridIndex: Int): Int {
        lookup(gridIndex)
        return cachedSectionIndex
    }

    fun isHeader(gridIndex: Int): Boolean {
        if (headerIndices.isEmpty()) return false
        lookup(gridIndex)
        return cachedIsHeader
    }

    /** Position in the paged media list for a non-header [gridIndex]. */
    fun mediaIndexAt(gridIndex: Int): Int {
        lookup(gridIndex)
        return gridIndex - cachedSectionIndex - 1
    }

    fun gridIndexForMedia(mediaIndex: Int): Int {
        if (sections.isEmpty()) return 0
        var low = 0
        var high = sections.size - 1
        var sectionIndex = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (sections[mid].startIndex <= mediaIndex) {
                sectionIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return mediaIndex + sectionIndex + 1
    }
}
