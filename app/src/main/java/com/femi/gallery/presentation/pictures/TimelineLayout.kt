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

    /** Index of the section that owns [gridIndex] (as header or photo). Requires non-empty sections. */
    fun sectionIndexAt(gridIndex: Int): Int {
        var low = 0
        var high = headerIndices.size - 1
        var answer = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (headerIndices[mid] <= gridIndex) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }

    fun isHeader(gridIndex: Int): Boolean =
        headerIndices.isNotEmpty() && headerIndices[sectionIndexAt(gridIndex)] == gridIndex

    /** Position in the paged media list for a non-header [gridIndex]. */
    fun mediaIndexAt(gridIndex: Int): Int = gridIndex - sectionIndexAt(gridIndex) - 1
}
