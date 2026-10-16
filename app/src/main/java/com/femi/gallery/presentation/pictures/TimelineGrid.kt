package com.femi.gallery.presentation.pictures

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import coil3.compose.AsyncImage
import com.femi.gallery.R
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.TimelineSection
import com.femi.gallery.presentation.viewer.formatDuration

private const val ContentTypeHeader = 0
private const val ContentTypeMedia = 1

/** Pinch distance (as a ratio) needed to move one zoom level. */
private const val ZoomStep = 1.25f

/**
 * The day-grouped photo grid with pinch-to-zoom columns and a fast scroller. Shared by the
 * Pictures tab and album screens; callers own the paging items and column count.
 *
 * [topInset] reserves space at the top of the grid (e.g. for the status bar).
 */
@Composable
fun TimelineGrid(
    items: LazyPagingItems<MediaItem>,
    sections: List<TimelineSection>,
    columns: Int,
    onMediaClick: (mediaIndex: Int) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
) {
    val layout = remember(sections) { TimelineLayout(sections) }
    val labeler = rememberDayLabeler()
    val gridState = rememberLazyGridState()

    // When a newer photo arrives, jump to it if the user is already near the top.
    val newestId = if (items.itemCount > 0) items.peek(0)?.id else null
    LaunchedEffect(newestId) {
        if (newestId != null && gridState.firstVisibleItemIndex < 24) gridState.scrollToItem(0)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pinchToChangeColumns(onZoomIn = onZoomIn, onZoomOut = onZoomOut),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topInset),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            items(
                count = layout.totalCount,
                key = { gridIndex ->
                    if (layout.isHeader(gridIndex)) {
                        "day-${layout.sections[layout.sectionIndexAt(gridIndex)].epochDay}"
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
                    DayHeader(text = labeler.dayLabel(section.epochDay))
                } else {
                    val mediaIndex = layout.mediaIndexAt(gridIndex)
                    MediaCell(
                        item = items.getOrNull(mediaIndex),
                        onClick = { onMediaClick(mediaIndex) },
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
                    labeler.monthLabel(layout.sections[layout.sectionIndexAt(gridIndex)].epochDay)
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

@Composable
private fun MediaCell(item: MediaItem?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = item != null, onClick = onClick),
    ) {
        if (item != null) {
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.isVideo) {
                VideoBadge(
                    durationMillis = item.durationMillis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                )
            }
        }
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
