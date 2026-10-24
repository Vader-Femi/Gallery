package com.femi.gallery.presentation.viewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.femi.gallery.domain.model.MediaItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** The height of the strip; pages that draw near the bottom (a video's controls) keep clear of it. */
internal val MediaStripHeight = 64.dp

private val ItemWidth = 40.dp
private val ItemHeight = 52.dp
private val ItemSpacing = 4.dp
private const val ThumbnailPixels = 160

/**
 * A row of small thumbnails along the bottom of the viewer, like Samsung Gallery's. The photo being
 * viewed sits in the middle, picked out by a border. Tap a thumbnail to go to that photo, or
 * drag the strip to scrub through the photos: the viewer follows whichever one is in the middle.
 * Swiping the photos themselves moves the strip to match.
 */
@Composable
internal fun MediaStrip(
    items: LazyPagingItems<MediaItem>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    onVideoItemTap: ((Int) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = pagerState.currentPage)
    // True while the strip is being moved by the code below rather than by a finger.
    var followingPager by remember { mutableStateOf(false) }

    val surface = MaterialTheme.colorScheme.surface
    val stripScrim = remember(surface) {
        Brush.verticalGradient(listOf(Color.Transparent, surface.copy(alpha = 0.45f)))
    }

    // Finger on the strip: the viewer follows the item in the middle.
    LaunchedEffect(listState, pagerState) {
        snapshotFlow { if (listState.isScrollInProgress && !followingPager) listState.centeredIndex() else null }
            .filterNotNull()
            .distinctUntilChanged()
            .collectLatest { pagerState.scrollToPage(it) }
    }
    // Swiping the photos: the strip follows once they settle (unless a finger is on the strip).
    LaunchedEffect(pagerState.settledPage) {
        if (listState.isScrollInProgress) return@LaunchedEffect
        followingPager = true
        try {
            listState.animateScrollToItem(pagerState.settledPage)
        } finally {
            followingPager = false
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(MediaStripHeight)
            .background(stripScrim),
    ) {
        // Room on both sides, so that the first and the last photo can also come to the middle.
        val sidePadding = (maxWidth - ItemWidth) / 2
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = sidePadding),
            horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
            flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Center),
        ) {
            items(
                count = items.itemCount,
                key = { index -> items.peekOrNull(index)?.id ?: (-index - 1L) },
            ) { index ->
                StripItem(
                    item = items[index],
                    selected = pagerState.currentPage == index,
                    onClick = {
                        if (pagerState.currentPage == index && items.peekOrNull(index)?.isVideo == true && onVideoItemTap != null) {
                            onVideoItemTap(index)
                        } else {
                            scope.launch {
                                if (MediaStripMath.shouldAnimate(pagerState.currentPage, index)) {
                                    pagerState.animateScrollToPage(index)
                                } else {
                                    pagerState.scrollToPage(index)
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun ExpandedVideoSeekBar(
    item: MediaItem,
    currentPositionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val seekRatio = remember(currentPositionMs, durationMs) {
        if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }
    val surface = MaterialTheme.colorScheme.surface
    val stripScrim = remember(surface) {
        Brush.verticalGradient(listOf(Color.Transparent, surface.copy(alpha = 0.45f)))
    }
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val playheadColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MediaStripHeight)
            .background(stripScrim)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (size.width > 0 && durationMs > 0) {
                            val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek((ratio * durationMs).toLong())
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        if (size.width > 0 && durationMs > 0) {
                            val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                            onSeek((ratio * durationMs).toLong())
                        }
                    }
                },
        ) {
            val width = maxWidth
            Row(modifier = Modifier.fillMaxSize()) {
                repeat(7) {
                    val request = remember(item.uri) {
                        ImageRequest.Builder(context).data(item.uri).size(ThumbnailPixels).build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .graphicsLayer {
                        translationX = (width.toPx() * seekRatio) - (2.dp.toPx())
                    }
                    .background(playheadColor, CircleShape),
            )
        }
    }
}

/** The index of the item in the middle of the strip, if any is showing. */
private fun LazyListState.centeredIndex(): Int? {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    return MediaStripMath.centeredIndex(
        indices = IntArray(visible.size) { visible[it].index },
        offsets = IntArray(visible.size) { visible[it].offset },
        sizes = IntArray(visible.size) { visible[it].size },
        viewportStart = info.viewportStartOffset,
        viewportEnd = info.viewportEndOffset,
    )
}

@Composable
private fun StripItem(item: MediaItem?, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val request = remember(item?.uri) {
        item?.let { ImageRequest.Builder(context).data(it.uri).size(ThumbnailPixels).build() }
    }
    val scale by animateFloatAsState(targetValue = if (selected) 1.15f else 1f, label = "strip item scale")
    val shape = RoundedCornerShape(6.dp)
    val borderColor = MaterialTheme.colorScheme.onSurface
    val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Box(
        Modifier
            .size(ItemWidth, ItemHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bgColor)
            .then(if (selected) Modifier.border(2.dp, borderColor, shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (item?.isVideo == true) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).size(18.dp),
            )
        }
    }
}
