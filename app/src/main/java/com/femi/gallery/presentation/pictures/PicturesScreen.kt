package com.femi.gallery.presentation.pictures

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.femi.gallery.R
import com.femi.gallery.domain.model.MediaItem

private const val ContentTypeHeader = 0
private const val ContentTypeMedia = 1

/** Pinch distance (as a ratio) needed to move one zoom level. */
private const val ZoomStep = 1.25f

@Composable
fun PicturesScreen(
    modifier: Modifier = Modifier,
    viewModel: PicturesViewModel = hiltViewModel(),
) {
    val items = viewModel.media.collectAsLazyPagingItems()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    val layout = remember(sections) { TimelineLayout(sections) }
    val labeler = rememberDayLabeler()
    val gridState = rememberLazyGridState()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    // When a newer photo arrives, jump to it if the user is already near the top.
    val newestId = if (items.itemCount > 0) items.peek(0)?.id else null
    LaunchedEffect(newestId) {
        if (newestId != null && gridState.firstVisibleItemIndex < 24) gridState.scrollToItem(0)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pinchToChangeColumns(onZoomIn = viewModel::zoomIn, onZoomOut = viewModel::zoomOut),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = statusBarTop),
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
                    MediaCell(item = items.getOrNull(layout.mediaIndexAt(gridIndex)))
                }
            }
        }

        FastScroller(
            gridState = gridState,
            totalItems = layout.totalCount,
            topInset = statusBarTop,
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
private fun MediaCell(item: MediaItem?) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
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

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
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
