package com.femi.gallery.presentation.viewer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
internal val MediaStripHeight = 60.dp

private val PictureItemWidth = 36.dp
private val VideoItemWidth = 64.dp
private val ItemHeight = 48.dp
private val ItemSpacing = 6.dp
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
    currentPositionMs: (() -> Long)? = null,
    durationMs: (() -> Long)? = null,
    onSeek: ((Long) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = pagerState.currentPage)
    // True while the strip is being moved by the code below rather than by a finger.
    var followingPager by remember { mutableStateOf(false) }
    var expandedPageIndex by remember { mutableStateOf<Int?>(null) }

    // Reset expansion when changing pages
    LaunchedEffect(pagerState.settledPage) {
        if (expandedPageIndex != pagerState.settledPage) {
            expandedPageIndex = null
        }
    }

    val surface = MaterialTheme.colorScheme.surface

    // Scrubbing video when expanded: swiping the strip across the expanded filmstrip seeks the video.
    LaunchedEffect(listState, pagerState, expandedPageIndex) {
        snapshotFlow {
            if (!listState.isScrollInProgress || followingPager) return@snapshotFlow null
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            val expIdx = expandedPageIndex
            val dur = durationMs?.invoke() ?: 0L

            if (expIdx != null && expIdx == pagerState.currentPage && dur > 0L) {
                val targetItem = visible.firstOrNull { it.index == expIdx }
                if (targetItem != null && targetItem.size > 0) {
                    val viewportMiddle = (info.viewportStartOffset + info.viewportEndOffset) / 2.0
                    val itemMiddleOffset = viewportMiddle - targetItem.offset
                    val ratio = (itemMiddleOffset / targetItem.size.toDouble()).coerceIn(0.0, 1.0)
                    return@snapshotFlow ratio
                }
            }
            null
        }
        .filterNotNull()
        .distinctUntilChanged()
        .collectLatest { ratio ->
            val dur = durationMs?.invoke() ?: 0L
            if (dur > 0L && onSeek != null) {
                onSeek((ratio * dur).toLong())
            }
        }
    }

    // Finger on the strip: the viewer follows the item in the middle (when not actively scrubbing an expanded video).
    LaunchedEffect(listState, pagerState, expandedPageIndex) {
        snapshotFlow {
            if (listState.isScrollInProgress && !followingPager && expandedPageIndex == null) {
                listState.centeredIndex()
            } else null
        }
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
            .background(surface),
    ) {
        val durMs = durationMs?.invoke() ?: 0L
        val firstIsVideo = items.itemCount > 0 && items.peekOrNull(0)?.isVideo == true
        val lastIsVideo = items.itemCount > 0 && items.peekOrNull(items.itemCount - 1)?.isVideo == true

        val firstIsExpanded = firstIsVideo && expandedPageIndex == 0
        val lastIsExpanded = lastIsVideo && expandedPageIndex == items.itemCount - 1

        val firstWidth = when {
            firstIsExpanded -> MediaStripMath.calculateExpandedWidth(durMs).dp
            firstIsVideo -> VideoItemWidth
            else -> PictureItemWidth
        }
        val lastWidth = when {
            lastIsExpanded -> MediaStripMath.calculateExpandedWidth(durMs).dp
            lastIsVideo -> VideoItemWidth
            else -> PictureItemWidth
        }

        val minSidePadding = maxWidth / 2
        val startPadding = ((maxWidth - firstWidth) / 2).coerceAtLeast(minSidePadding)
        val endPadding = ((maxWidth - lastWidth) / 2).coerceAtLeast(minSidePadding)

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = startPadding, end = endPadding),
            horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
            flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Center),
        ) {
            items(count = items.itemCount) { index ->
                val selected = remember(index, pagerState) { derivedStateOf { pagerState.currentPage == index } }
                val isExpanded = expandedPageIndex == index
                StripItem(
                    item = items[index],
                    selected = selected,
                    isExpanded = isExpanded,
                    currentPositionMs = if (pagerState.currentPage == index) currentPositionMs else null,
                    durationMs = if (pagerState.currentPage == index) durationMs else null,
                    onClick = {
                        if (pagerState.currentPage == index) {
                            if (items.peekOrNull(index)?.isVideo == true) {
                                if (expandedPageIndex == index) {
                                    expandedPageIndex = null
                                } else {
                                    expandedPageIndex = index
                                    scope.launch {
                                        listState.animateScrollToItem(index)
                                    }
                                }
                                onVideoItemTap?.invoke(index)
                            }
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
private fun StripItem(
    item: MediaItem?,
    selected: State<Boolean>,
    isExpanded: Boolean,
    onClick: () -> Unit,
    currentPositionMs: (() -> Long)? = null,
    durationMs: (() -> Long)? = null,
) {
    val isSelected by selected
    val context = LocalContext.current
    val isVideo = item?.isVideo == true
    val dur = durationMs?.invoke() ?: 0L

    val targetWidth = when {
        isVideo && isExpanded -> MediaStripMath.calculateExpandedWidth(dur).dp
        isVideo -> VideoItemWidth
        else -> PictureItemWidth
    }

    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "stripItemWidth",
    )

    val shape = RoundedCornerShape(6.dp)
    val borderColor = MaterialTheme.colorScheme.onSurface
    val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Box(
        Modifier
            .width(animatedWidth)
            .height(ItemHeight)
            .clip(shape)
            .background(bgColor)
            .then(if (isSelected) Modifier.border(2.dp, borderColor, shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        if (item != null) {
            if (isVideo && isExpanded) {
                val frameCount = (targetWidth.value / 36f).toInt().coerceAtLeast(3)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    repeat(frameCount) {
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
            } else {
                val request = remember(item.uri) {
                    ImageRequest.Builder(context).data(item.uri).size(ThumbnailPixels).build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Seek head & progress bar: ALWAYS visible on video items when selected and with position
        if (isVideo && isSelected && currentPositionMs != null && durationMs != null) {
            VideoStripSeekHead(
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun VideoStripSeekHead(
    currentPositionMs: () -> Long,
    durationMs: () -> Long,
    modifier: Modifier = Modifier,
) {
    val trackColor = Color.Black.copy(alpha = 0.5f)
    val progressColor = MaterialTheme.colorScheme.primary
    val playheadColor = Color.White

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart,
    ) {
        val width = maxWidth
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.BottomStart)
                .background(trackColor),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.BottomStart)
                .graphicsLayer {
                    val dur = durationMs()
                    val ratio = if (dur > 0) (currentPositionMs().toFloat() / dur).coerceIn(0f, 1f) else 0f
                    scaleX = ratio
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                }
                .background(progressColor),
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .graphicsLayer {
                    val dur = durationMs()
                    val ratio = if (dur > 0) (currentPositionMs().toFloat() / dur).coerceIn(0f, 1f) else 0f
                    translationX = (width.toPx() * ratio) - (1.5.dp.toPx())
                }
                .background(playheadColor, CircleShape),
        )
    }
}
