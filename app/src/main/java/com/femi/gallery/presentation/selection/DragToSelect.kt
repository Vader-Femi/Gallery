package com.femi.gallery.presentation.selection

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toIntRect
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    val haptic = LocalHapticFeedback.current

    Modifier.pointerInput(Unit) {
        val longPressTimeout = viewConfiguration.longPressTimeoutMillis
        val touchSlop = viewConfiguration.touchSlop
        val autoScrollThresholdPx = 60.dp.toPx()

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

            val initialMediaIndex = mediaIndexAtOffsetOrEdge(gridState, down.position, isHeader, mediaIndexAt)
            if (initialMediaIndex < 0) {
                return@awaitEachGesture
            }

            var isLongPressed = false

            // Wait for long-press duration using withTimeoutOrNull so timer triggers even if finger is still
            val longPressResult = withTimeoutOrNull(longPressTimeout) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                    if (change.changedToUp() || !change.pressed) {
                        break
                    }

                    val distance = (change.position - down.position).getDistance()
                    if (distance > touchSlop) {
                        break
                    }
                }
            }

            // If withTimeoutOrNull returned null, timeout expired without finger up or move past touch slop -> Long press detected!
            if (longPressResult == null) {
                isLongPressed = true
            }

            if (isLongPressed) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongPressStart(initialMediaIndex)

                var currentMediaIndex = initialMediaIndex
                var currentPointerPosition = down.position
                var autoScrollJob: Job? = null

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
                                    val mediaIdx = mediaIndexAtOffsetOrEdge(gridState, currentPointerPosition, isHeader, mediaIndexAt)
                                    if (mediaIdx >= 0 && mediaIdx != currentMediaIndex) {
                                        currentMediaIndex = mediaIdx
                                        onDragSelectRange(initialMediaIndex, mediaIdx)
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
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull { it.pressed }
                            ?: break

                        if (change.changedToUp() || !change.pressed) {
                            break
                        }

                        change.consume()
                        currentPointerPosition = change.position
                        updateAutoScroll(change.position)

                        val mediaIdx = mediaIndexAtOffsetOrEdge(gridState, change.position, isHeader, mediaIndexAt)
                        if (mediaIdx >= 0 && mediaIdx != currentMediaIndex) {
                            currentMediaIndex = mediaIdx
                            onDragSelectRange(initialMediaIndex, mediaIdx)
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
 * Finds the media index of the visible item at [offset].
 * If [offset] is above or below the visible grid items (e.g. during auto-scroll),
 * resolves to the first or last visible media item index.
 */
private fun mediaIndexAtOffsetOrEdge(
    gridState: LazyGridState,
    offset: Offset,
    isHeader: (gridIndex: Int) -> Boolean,
    mediaIndexAt: (gridIndex: Int) -> Int,
): Int {
    val visibleItems = gridState.layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return -1

    val rounded = offset.round()
    val hitItem = visibleItems.firstOrNull { item ->
        item.size.toIntRect().contains(rounded - item.offset)
    }

    if (hitItem != null) {
        if (!isHeader(hitItem.index)) {
            return mediaIndexAt(hitItem.index)
        }
        return -1
    }

    // Fallback for dragging slightly above or below visible bounds during auto-scroll
    val minTop = visibleItems.minOf { it.offset.y }
    val maxBottom = visibleItems.maxOf { it.offset.y + it.size.height }

    if (rounded.y < minTop) {
        val firstMediaItem = visibleItems.firstOrNull { !isHeader(it.index) }
        if (firstMediaItem != null) {
            return mediaIndexAt(firstMediaItem.index)
        }
    } else if (rounded.y > maxBottom) {
        val lastMediaItem = visibleItems.lastOrNull { !isHeader(it.index) }
        if (lastMediaItem != null) {
            return mediaIndexAt(lastMediaItem.index)
        }
    }

    return -1
}


