package com.femi.gallery.presentation.selection

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toIntRect

private const val TAG = "DragToSelect"

/**
 * A pointer-input modifier that implements drag-to-select across grid cells.
 *
 * Uses [PointerEventPass.Initial] so that:
 * 1. Child clicks or long-clicks cannot consume events out from under our drag gesture recognizer.
 * 2. Normal scrolls and flings are untouched: if the finger moves beyond touch slop before
 *    the long-press duration expires, this gesture detector yields to normal scrolling.
 * 3. Once a long-press on an item is confirmed, subsequent pointer movement consumes the events,
 *    preventing the grid from scrolling, and continuously reports the range between the start
 *    item and the item under the finger.
 */
fun Modifier.dragToSelect(
    gridState: LazyGridState,
    isHeader: (gridIndex: Int) -> Boolean,
    mediaIndexAt: (gridIndex: Int) -> Int,
    onLongPressStart: (mediaIndex: Int) -> Unit,
    onDragSelectRange: (startIndex: Int, endIndex: Int) -> Unit,
): Modifier = pointerInput(gridState) {
    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
    val touchSlop = viewConfiguration.touchSlop

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        Log.d(TAG, "Down at ${down.position}")
        var isLongPressed = false
        var initialMediaIndex = -1
        var currentMediaIndex = -1

        val startUptime = down.uptimeMillis

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break

            if (change.changedToUp()) {
                Log.d(TAG, "Pointer up before long-press timeout reached")
                break
            }

            val distance = (change.position - down.position).getDistance()
            if (distance > touchSlop) {
                Log.d(TAG, "Movement exceeded touchSlop ($distance > $touchSlop) -> normal scroll")
                break
            }

            if (change.uptimeMillis - startUptime >= longPressTimeout) {
                val initialGridIndex = gridIndexAtOffset(gridState, down.position)
                Log.d(TAG, "Long-press reached! initialGridIndex=$initialGridIndex")
                if (initialGridIndex >= 0 && !isHeader(initialGridIndex)) {
                    val mediaIdx = mediaIndexAt(initialGridIndex)
                    isLongPressed = true
                    initialMediaIndex = mediaIdx
                    currentMediaIndex = mediaIdx
                    Log.d(TAG, "Triggering onLongPressStart(mediaIdx=$mediaIdx)")
                    onLongPressStart(mediaIdx)
                }
                break
            }
        }

        if (isLongPressed) {
            Log.d(TAG, "Entering drag tracking loop")
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUp()) {
                    Log.d(TAG, "Drag ended (finger up)")
                    break
                }

                // Consume so LazyVerticalGrid doesn't scroll
                change.consume()

                val gridIndex = gridIndexAtOffset(gridState, change.position)
                if (gridIndex >= 0 && !isHeader(gridIndex)) {
                    val mediaIdx = mediaIndexAt(gridIndex)
                    if (mediaIdx != currentMediaIndex) {
                        currentMediaIndex = mediaIdx
                        Log.d(TAG, "Drag over mediaIdx=$mediaIdx, range: $initialMediaIndex..$mediaIdx")
                        onDragSelectRange(initialMediaIndex, mediaIdx)
                    }
                }
            }
        }
    }
}

/**
 * Finds the grid index of the visible item at [offset] relative to the grid's top-left,
 * or -1 if no item is hit.
 */
private fun gridIndexAtOffset(gridState: LazyGridState, offset: Offset): Int {
    val rounded = offset.round()
    return gridState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
        item.size.toIntRect().contains(rounded - item.offset)
    }?.index ?: -1
}
