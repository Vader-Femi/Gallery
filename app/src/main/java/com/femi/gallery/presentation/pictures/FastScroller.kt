package com.femi.gallery.presentation.pictures

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val ThumbHeight = 56.dp
private val ThumbTouchWidth = 36.dp
private const val HideDelayMs = 1_500L

/**
 * Samsung-style scrubber: appears while scrolling, can be dragged to jump anywhere in the
 * timeline and shows the month under the thumb. Jumps are instant because the paged list
 * has placeholders for every item.
 */
@Composable
fun BoxScope.FastScroller(
    gridState: LazyGridState,
    totalItems: Int,
    topInset: Dp,
    labelFor: (gridIndex: Int) -> String,
) {
    val thumbHeightPx = with(LocalDensity.current) { ThumbHeight.toPx() }
    val currentTotal by rememberUpdatedState(totalItems)
    val currentLabelFor by rememberUpdatedState(labelFor)
    val haptic = LocalHapticFeedback.current

    var trackHeightPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var recentlyScrolled by remember { mutableStateOf(false) }
    var lastHapticLabel by remember { mutableStateOf<String?>(null) }

    val scrolling = gridState.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) {
            recentlyScrolled = true
        } else {
            delay(HideDelayMs)
            recentlyScrolled = false
        }
    }

    val scrollFraction by remember {
        derivedStateOf {
            val visibleCount = gridState.layoutInfo.visibleItemsInfo.size
            val denominator = (currentTotal - visibleCount).coerceAtLeast(1)
            (gridState.firstVisibleItemIndex.toFloat() / denominator).coerceIn(0f, 1f)
        }
    }

    val fraction = if (dragging) dragFraction else scrollFraction

    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .fillMaxHeight()
            .padding(top = topInset)
            .onSizeChanged { trackHeightPx = it.height }
            // Apply tap gestures without a fixed width so it only acts on the thumb's bounding box area,
            // or we remove the track tap-to-jump to prevent interfering with normal grid scrolling.
    ) {
        AnimatedVisibility(
            visible = (recentlyScrolled || dragging) && currentTotal > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.offset {
                val travel = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
                IntOffset(0, (travel * fraction).roundToInt())
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dragging) {
                    val maxIndex = (currentTotal - 1).coerceAtLeast(0)
                    val targetIndex = (dragFraction * maxIndex).roundToInt().coerceIn(0, maxIndex)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(
                            text = currentLabelFor(targetIndex),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .width(ThumbTouchWidth)
                        .height(ThumbHeight)
                        .systemGestureExclusion()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragFraction = scrollFraction
                                    dragging = true
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val travel = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
                                    dragFraction = (dragFraction + dragAmount / travel).coerceIn(0f, 1f)
                                    val last = (currentTotal - 1).coerceAtLeast(0)
                                    val target = (dragFraction * last).roundToInt()
                                    gridState.requestScrollToItem(target)
                                    
                                    val currentLabel = currentLabelFor(target)
                                    if (currentLabel != lastHapticLabel) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        lastHapticLabel = currentLabel
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .width(8.dp)
                            .height(ThumbHeight - 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(4.dp),
                            ),
                    )
                }
            }
        }
    }
}
