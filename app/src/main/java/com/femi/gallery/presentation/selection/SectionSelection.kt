package com.femi.gallery.presentation.selection

import com.femi.gallery.domain.model.MediaRef
import androidx.paging.compose.LazyPagingItems
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.TimelineSection
import com.femi.gallery.domain.model.toRef
import kotlin.math.max
import kotlin.math.min

/**
 * Toggles selection of all items in [section]:
 * If every loaded item in the section is already selected, removes them all from the selection;
 * otherwise adds all loaded items in the section to the selection.
 */
fun toggleSectionSelection(
    section: TimelineSection,
    items: LazyPagingItems<MediaItem>,
    selectedIds: Set<Long>,
    controller: MediaSelectionController,
) {
    toggleSectionSelection(section, items, selectedIds, controller::addAll, controller::removeAll)
}

/**
 * Overload for screens that only have a [SelectionManager] (such as the photo picker).
 */
fun toggleSectionSelection(
    section: TimelineSection,
    items: LazyPagingItems<MediaItem>,
    selectedIds: Set<Long>,
    manager: SelectionManager,
) {
    toggleSectionSelection(section, items, selectedIds, manager::addAll, manager::removeAll)
}

private inline fun toggleSectionSelection(
    section: TimelineSection,
    items: LazyPagingItems<MediaItem>,
    selectedIds: Set<Long>,
    addAll: (List<com.femi.gallery.domain.model.MediaRef>) -> Unit,
    removeAll: (Set<Long>) -> Unit,
) {
    val refs = (section.startIndex until section.startIndex + section.count).mapNotNull { i ->
        if (i in 0 until items.itemCount) items.peek(i)?.toRef() else null
    }
    if (refs.isEmpty()) return
    val allSelected = refs.all { it.id in selectedIds }
    if (allSelected) {
        removeAll(refs.map { it.id }.toSet())
    } else {
        addAll(refs)
    }
}

/**
 * Selects all loaded media items in the index range between [startIndex] and [endIndex] inclusive,
 * preserving any previously selected items in [baseSelected] while dynamically updating the active drag range.
 */
fun selectMediaRange(
    startIndex: Int,
    endIndex: Int,
    items: LazyPagingItems<MediaItem>,
    controller: MediaSelectionController,
    baseSelected: Map<Long, MediaRef> = emptyMap(),
) {
    val refs = getMediaRangeRefs(startIndex, endIndex, items)
    controller.setDragRange(baseSelected, refs)
}

/**
 * Overload of [selectMediaRange] for [SelectionManager].
 */
fun selectMediaRange(
    startIndex: Int,
    endIndex: Int,
    items: LazyPagingItems<MediaItem>,
    manager: SelectionManager,
    baseSelected: Map<Long, MediaRef> = emptyMap(),
) {
    val refs = getMediaRangeRefs(startIndex, endIndex, items)
    manager.setDragRange(baseSelected, refs)
}

fun getMediaRangeRefs(
    startIndex: Int,
    endIndex: Int,
    items: LazyPagingItems<MediaItem>,
): List<MediaRef> {
    val low = max(0, min(startIndex, endIndex))
    val high = min(items.itemCount - 1, max(startIndex, endIndex))
    if (low > high) return emptyList()
    return (low..high).mapNotNull { i -> items.peek(i)?.toRef() }
}
