package com.femi.gallery.presentation.selection

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toIntRect
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A pointer-input modifier that implements drag-to-select across grid cells.
 *
 * 1. The first long-press immediately enters selection and begins dragging across cells in the
 *    very same gesture (no second long-press required!).
 * 2. If the user moves beyond touch slop before the long-press duration expires, it yields to
 *    normal scrolling/flinging.
 * 3. When dragging near the top or bottom edges of the grid, auto-scrolls smoothly so the user
 *    can select large ranges in a single session.
 */
fun Modifier.dragToSelect(
    gridState: LazyGridState,
    isHeader: (gridIndex: Int) -> Boolean,
    mediaIndexAt: (gridIndex: Int) -> Int,
    onLongPressStart: (mediaIndex: Int) -> Unit,
    onDragSelectRange: (startIndex: Int, endIndex: Int) -> Unit,
): Modifier = composed {
    val coroutineScope = rememberCoroutineScope()

    Modifier.pointerInput(gridState) {
        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
        val touchSlop = viewConfiguration.touchSlop
        val autoScrollThresholdPx = 60.dp.toPx()

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var isLongPressed = false
            var initialMediaIndex = -1
            var currentMediaIndex = -1
            var currentPointerPosition = down.position

            var autoScrollJob: Job? = null

            val startUptime = down.uptimeMillis

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                if (change.changedToUp()) {
                    break
                }

                val distance = (change.position - down.position).getDistance()
                if (distance > touchSlop) {
                    break
                }

                if (change.uptimeMillis - startUptime >= longPressTimeout) {
                    val initialGridIndex = gridIndexAtOffset(gridState, down.position)
                    if (initialGridIndex >= 0 && !isHeader(initialGridIndex)) {
                        val mediaIdx = mediaIndexAt(initialGridIndex)
                        isLongPressed = true
                        initialMediaIndex = mediaIdx
                        currentMediaIndex = mediaIdx
                        onLongPressStart(mediaIdx)
                    }
                    break
                }
            }

            if (isLongPressed) {
                // Auto-scroll loop when finger drags near top/bottom edges
                fun updateAutoScroll(position: Offset) {
                    val viewportHeight = gridState.layoutInfo.viewportSize.height.toFloat()
                    val scrollSpeed: Float = when {
                        position.y < autoScrollThresholdPx -> {
                            val ratio = (autoScrollThresholdPx - position.y.coerceAtLeast(0f)) / autoScrollThresholdPx
                            -15f * (1f + ratio * 2f)
                        }
                        position.y > viewportHeight - autoScrollThresholdPx -> {
                            val ratio = (position.y - (viewportHeight - autoScrollThresholdPx)).coerceAtLeast(0f) / autoScrollThresholdPx
                            15f * (1f + ratio * 2f)
                        }
                        else -> 0f
                    }

                    if (scrollSpeed != 0f) {
                        if (autoScrollJob == null || autoScrollJob?.isActive == false) {
                            autoScrollJob = coroutineScope.launch {
                                while (isActive) {
                                    gridState.scrollBy(scrollSpeed)
                                    val gridIndex = gridIndexAtOffset(gridState, currentPointerPosition)
                                    if (gridIndex >= 0 && !isHeader(gridIndex)) {
                                        val mediaIdx = mediaIndexAt(gridIndex)
                                        if (mediaIdx != currentMediaIndex) {
                                            currentMediaIndex = mediaIdx
                                            onDragSelectRange(initialMediaIndex, mediaIdx)
                                        }
                                    }
                                    delay(16.milliseconds) // ~60 FPS
                                }
                            }
                        }
                    } else {
                        autoScrollJob?.cancel()
                        autoScrollJob = null
                    }
                }

                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUp()) {
                            break
                        }

                        change.consume()
                        currentPointerPosition = change.position
                        updateAutoScroll(change.position)

                        val gridIndex = gridIndexAtOffset(gridState, change.position)
                        if (gridIndex >= 0 && !isHeader(gridIndex)) {
                            val mediaIdx = mediaIndexAt(gridIndex)
                            if (mediaIdx != currentMediaIndex) {
                                currentMediaIndex = mediaIdx
                                onDragSelectRange(initialMediaIndex, mediaIdx)
                            }
                        }
                    }
                } finally {
                    autoScrollJob?.cancel()
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
