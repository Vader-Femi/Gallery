package com.femi.gallery.presentation.pictures

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.femi.gallery.R
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.TimelineSection
import com.femi.gallery.presentation.viewer.formatDuration

private const val ContentTypeHeader = 0
private const val ContentTypeMedia = 1
private const val GridThumbnailSize = 512

/** Pinch distance (as a ratio) needed to move one zoom level. */
private const val ZoomStep = 1.25f

/**
 * The grouped photo grid with pinch-to-zoom levels (days, months, years) and a fast scroller. Shared by the
 * Pictures tab and album screens; callers own the paging items and column count.
 *
 * [topInset] reserves space at the top of the grid (e.g. for the status bar).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TimelineGrid(
    items: LazyPagingItems<MediaItem>,
    sections: List<TimelineSection>,
    zoom: TimelineZoom,
    onMediaClick: (mediaIndex: Int, item: MediaItem) -> Unit,
    onMediaLongClick: (item: MediaItem) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    selectionActive: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    lastViewedMediaIndex: Int? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val layout = remember(sections) { TimelineLayout(sections) }
    val labeler = rememberDayLabeler()
    val gridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var anchorMediaIndex by remember { mutableIntStateOf(-1) }

    // Scroll to the item viewed in the full-screen viewer if it is scrolled offscreen.
    LaunchedEffect(lastViewedMediaIndex) {
        if (lastViewedMediaIndex != null && lastViewedMediaIndex >= 0 && layout.totalCount > 0) {
            val gridIndex = layout.gridIndexForMedia(lastViewedMediaIndex)
            val isVisible = gridState.layoutInfo.visibleItemsInfo.any { it.index == gridIndex }
            if (!isVisible) {
                gridState.scrollToItem(gridIndex)
            }
        }
    }
    
    // Check strictly if at the very top of the grid
    val isAtTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0 }
    }

    // When a newer photo arrives, jump to it if the user is already strictly at the top.
    val newestId = if (items.itemCount > 0) items.peek(0)?.id else null
    LaunchedEffect(newestId) {
        if (newestId != null && isAtTop) {
            gridState.animateScrollToItem(0)
        }
    }

    // Restore scroll position after a layout change (e.g. from zoom)
    LaunchedEffect(layout) {
        if (anchorMediaIndex >= 0 && layout.totalCount > 0 && !isAtTop) {
            gridState.scrollToItem(layout.gridIndexForMedia(anchorMediaIndex))
        }
    }
    
    fun captureAnchorMediaIndex() {
        if (layout.totalCount > 0) {
            val gridIndex = gridState.firstVisibleItemIndex
            if (gridIndex < layout.totalCount) {
                anchorMediaIndex = if (layout.isHeader(gridIndex)) {
                    layout.sections[layout.sectionIndexAt(gridIndex)].startIndex
                } else {
                    layout.mediaIndexAt(gridIndex)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pinchToChangeColumns(
                onZoomIn = {
                    captureAnchorMediaIndex()
                    onZoomIn()
                    if (zoom == TimelineZoom.Day2) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onZoomOut = {
                    captureAnchorMediaIndex()
                    onZoomOut()
                    if (zoom == TimelineZoom.Year7) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            ),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(zoom.columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topInset, bottom = navBarBottom + 80.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            items(
                count = layout.totalCount,
                key = { gridIndex ->
                    if (layout.isHeader(gridIndex)) {
                        "${zoom.granularity}-${layout.sections[layout.sectionIndexAt(gridIndex)].epochDay}"
                    } else {
                        val mediaIndex = layout.mediaIndexAt(gridIndex)
                        items.peekOrNull(mediaIndex)?.id ?: (-mediaIndex - 1L)
                    }
                },
                span = { gridIndex ->
                    if (layout.isHeader(gridIndex)) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                },
                contentType = { gridIndex ->
                    if (layout.isHeader(gridIndex)) ContentTypeHeader else ContentTypeMedia
                },
            ) { gridIndex ->
                if (layout.isHeader(gridIndex)) {
                    val section = layout.sections[layout.sectionIndexAt(gridIndex)]
                    DayHeader(text = labeler.headerLabel(section.epochDay, zoom.granularity))
                } else {
                    val mediaIndex = layout.mediaIndexAt(gridIndex)
                    val item = items.getOrNull(mediaIndex)
                    MediaCell(
                        item = item,
                        mediaIndex = mediaIndex,
                        selectionActive = selectionActive,
                        selected = item != null && item.id in selectedIds,
                        onClick = onMediaClick,
                        onLongClick = onMediaLongClick,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }
        }

        FastScroller(
            gridState = gridState,
            totalItems = layout.totalCount,
            topInset = topInset,
            labelFor = { gridIndex ->
                if (layout.sections.isEmpty()) {
                    ""
                } else {
                    labeler.scrollLabel(layout.sections[layout.sectionIndexAt(gridIndex)].epochDay, zoom.granularity)
                }
            },
        )
    }
}

@Composable
private fun DayHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun MediaCell(
    item: MediaItem?,
    mediaIndex: Int,
    selectionActive: Boolean,
    selected: Boolean,
    onClick: (Int, MediaItem) -> Unit,
    onLongClick: (MediaItem) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics {
                if (item != null) {
                    contentDescription = if (item.isVideo) "Video, ${formatDuration(item.durationMillis)}" else "Photo"
                }
            }
            .combinedClickable(
                enabled = item != null,
                onClick = { if (item != null) onClick(mediaIndex, item) },
                onLongClick = { if (item != null) onLongClick(item) }
            ),
    ) {
        if (item != null) {
            val imageRequest = remember(item.uri) {
                ImageRequest.Builder(context)
                    .data(item.uri)
                    .size(GridThumbnailSize)
                    .memoryCacheKey("thumb_${item.id}")
                    .build()
            }
            val baseModifier = Modifier
                .fillMaxSize()
                .padding(if (selected) 6.dp else 0.dp)
            val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    baseModifier.sharedElement(
                        sharedContentState = rememberSharedContentState(key = "media_${item.id}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = { _, _ ->
                            androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                            )
                        },
                    )
                }
            } else {
                baseModifier
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Selected photos shrink slightly, like Samsung Gallery.
                modifier = sharedModifier,
            )
            if (item.isVideo) {
                VideoBadge(
                    durationMillis = item.durationMillis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                )
            }
            if (item.isFavorite && !selectionActive) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(14.dp),
                )
            }
            if (selectionActive) {
                SelectionIndicator(
                    selected = selected,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean, modifier: Modifier = Modifier) {
    val base = modifier
        .size(22.dp)
        .clip(CircleShape)
    if (selected) {
        Box(
            modifier = base.background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    } else {
        Box(modifier = base.background(Color.Black.copy(alpha = 0.25f)).border(2.dp, Color.White, CircleShape))
    }
}

@Composable
private fun VideoBadge(durationMillis: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(percent = 50))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = formatDuration(durationMillis),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(end = 2.dp),
        )
    }
}

@Composable
private fun rememberDayLabeler(): DayLabeler {
    val today = stringResource(R.string.timeline_today)
    val yesterday = stringResource(R.string.timeline_yesterday)
    return remember(today, yesterday) { DayLabeler(today, yesterday) }
}

private fun LazyPagingItems<MediaItem>.peekOrNull(index: Int): MediaItem? =
    if (index in 0 until itemCount) peek(index) else null

private fun LazyPagingItems<MediaItem>.getOrNull(index: Int): MediaItem? =
    if (index in 0 until itemCount) get(index) else null

/**
 * Two-finger pinch changes the number of columns. Runs in the Initial pass and consumes
 * multi-touch events so the grid doesn't scroll while pinching.
 */
private fun Modifier.pinchToChangeColumns(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var accumulated = 1f
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                accumulated *= event.calculateZoom()
                event.changes.forEach { it.consume() }
                when {
                    accumulated > ZoomStep -> {
                        onZoomIn()
                        accumulated = 1f
                    }
                    accumulated < 1f / ZoomStep -> {
                        onZoomOut()
                        accumulated = 1f
                    }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
