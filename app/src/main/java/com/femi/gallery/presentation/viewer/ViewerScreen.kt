package com.femi.gallery.presentation.viewer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.femi.gallery.R
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.toRef
import com.femi.gallery.presentation.common.findActivity
import com.femi.gallery.presentation.locked.LockedGate
import com.femi.gallery.presentation.selection.SelectionHost
import com.femi.gallery.presentation.selection.lockWithScreenLockCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import androidx.media3.common.MediaItem as PlayerMediaItem

private const val ThumbnailRequestSize = 512
private const val FullRequestSize = 2160

private val TopGradient = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))
private val BottomGradient = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ViewerScreen(
    onBack: () -> Unit,
    onPageChange: (mediaIndex: Int) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    if (viewModel.isLocked) {
        // Photos from the Locked folder only show after the user authenticates.
        LockedGate(session = viewModel.lockSession, onClose = onBack) {
            ViewerContent(onBack, onPageChange, viewModel, sharedTransitionScope, animatedVisibilityScope)
        }
    } else {
        ViewerContent(onBack, onPageChange, viewModel, sharedTransitionScope, animatedVisibilityScope)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ViewerContent(
    onBack: () -> Unit,
    onPageChange: (mediaIndex: Int) -> Unit,
    viewModel: ViewerViewModel,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val items = viewModel.media.collectAsLazyPagingItems()
    val details by viewModel.details.collectAsStateWithLifecycle()
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current

    ImmersiveMode(enabled = !chromeVisible)
    SelectionHost(controller = viewModel.actions, onSuccess = {})

    // Leave the viewer once the last photo has been moved to the trash.
    LaunchedEffect(items.loadState.refresh, items.itemCount) {
        if (items.loadState.refresh is LoadState.NotLoading && items.itemCount == 0) onBack()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (items.itemCount > 0) {
            val pagerState = rememberPagerState(
                initialPage = viewModel.startIndex.coerceIn(0, items.itemCount - 1),
            ) { items.itemCount }

            LaunchedEffect(pagerState.settledPage) {
                onPageChange(pagerState.settledPage)
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { page -> items.peekOrNull(page)?.id ?: (-page - 1L) },
            ) { page ->
                val handleChromeChange: (Boolean) -> Unit = remember { { chromeVisible = it } }
                ViewerPage(
                    item = items.getOrNull(page),
                    isCurrent = pagerState.settledPage == page,
                    chromeVisible = chromeVisible,
                    onChromeChange = handleChromeChange,
                    onBack = onBack,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }

            val current = items.peekOrNull(pagerState.currentPage)

            AnimatedVisibility(
                visible = chromeVisible && current != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                if (current != null) TopBar(item = current, onBack = onBack)
            }

            AnimatedVisibility(
                visible = chromeVisible && current != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                if (current != null) {
                    val ref = current.toRef()
                    val actions = when {
                        viewModel.isRecycleBin -> listOf(
                            ViewerAction(Icons.Outlined.RestoreFromTrash, stringResource(R.string.action_restore)) {
                                viewModel.actions.restore(listOf(ref))
                            },
                            ViewerAction(Icons.Outlined.DeleteForever, stringResource(R.string.action_delete_forever)) {
                                viewModel.actions.deleteForever(listOf(ref))
                            },
                            ViewerAction(Icons.Outlined.Info, stringResource(R.string.viewer_info)) {
                                viewModel.showDetails(current)
                            },
                        )
                        viewModel.isLocked -> listOf(
                            ViewerAction(Icons.Outlined.LockOpen, stringResource(R.string.action_unlock)) {
                                viewModel.actions.unlock(listOf(ref))
                            },
                            ViewerAction(Icons.Outlined.Delete, stringResource(R.string.action_delete)) {
                                viewModel.actions.deleteLocked(listOf(ref))
                            },
                            ViewerAction(Icons.Outlined.Info, stringResource(R.string.viewer_info)) {
                                viewModel.showDetails(current)
                            },
                        )
                        else -> listOf(
                            ViewerAction(Icons.Outlined.Share, stringResource(R.string.viewer_share)) {
                                shareMedia(context, current)
                            },
                            ViewerAction(
                                icon = if (current.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                label = stringResource(if (current.isFavorite) R.string.action_unfavorite else R.string.action_favorite),
                            ) { viewModel.actions.toggleFavorite(listOf(ref)) },
                            ViewerAction(Icons.Outlined.Delete, stringResource(R.string.action_delete)) {
                                viewModel.actions.trash(listOf(ref))
                            },
                            ViewerAction(Icons.Outlined.Lock, stringResource(R.string.action_lock)) {
                                lockWithScreenLockCheck(context) { viewModel.actions.lock(listOf(ref)) }
                            },
                            ViewerAction(Icons.Outlined.Info, stringResource(R.string.viewer_info)) {
                                viewModel.showDetails(current)
                            },
                        )
                    }
                    val cachedActions = remember(ref, viewModel.isRecycleBin, viewModel.isLocked, current.isFavorite) { actions }
                    ActionBar(actions = cachedActions)
                }
            }
        }
    }

    details?.let { DetailsSheet(details = it, onDismiss = viewModel::hideDetails) }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ViewerPage(
    item: MediaItem?,
    isCurrent: Boolean,
    chromeVisible: Boolean,
    onChromeChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    when {
        item == null -> Box(Modifier.fillMaxSize())
        item.isVideo -> VideoPage(
            item = item,
            isCurrent = isCurrent,
            onBack = onBack,
            onChromeChange = onChromeChange,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
        )
        else -> PhotoPage(
            item = item,
            isCurrent = isCurrent,
            onTap = { onChromeChange(!chromeVisible) },
            onBack = onBack,
            onChromeChange = onChromeChange,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PhotoPage(
    item: MediaItem,
    isCurrent: Boolean,
    onTap: () -> Unit,
    onBack: () -> Unit,
    onChromeChange: (Boolean) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val context = LocalContext.current
    var isZoomed by remember { mutableStateOf(false) }

    val thumbnail = remember(item.uri) {
        ImageRequest.Builder(context).data(item.uri).size(ThumbnailRequestSize).build()
    }
    val full = remember(item.uri) {
        ImageRequest.Builder(context).data(item.uri).size(FullRequestSize).crossfade(true).build()
    }
    val handleTap = remember(onTap) { { onTap() } }

    val aspectRatio = remember(item.width, item.height) {
        if (item.width > 0 && item.height > 0) item.width.toFloat() / item.height.toFloat() else null
    }
    var loadedSize by remember { mutableStateOf(Size.Zero) }
    val effectiveAspectRatio = aspectRatio ?: if (loadedSize.width > 0f && loadedSize.height > 0f) {
        loadedSize.width / loadedSize.height
    } else {
        null
    }

    DragToDismissBox(
        enabled = isCurrent && !isZoomed,
        onDismiss = onBack,
        onDragStateChange = { isDragging ->
            if (isDragging) onChromeChange(false)
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            ZoomableBox(
                isCurrent = isCurrent,
                onTap = handleTap,
                onZoomStateChange = { isZoomed = it },
            ) { onImageSize ->
                val baseModifier = if (effectiveAspectRatio != null) {
                    Modifier.aspectRatio(effectiveAspectRatio)
                } else {
                    Modifier.fillMaxSize()
                }

                val sharedModifier = if (isCurrent && sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        baseModifier.sharedElement(
                            sharedContentState = rememberSharedContentState(key = "media_${item.id}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ ->
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                )
                            },
                        )
                    }
                } else {
                    baseModifier
                }

                Box(modifier = sharedModifier) {
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { if (aspectRatio == null) onImageSize(it.painter.intrinsicSize) },
                    )
                    AsyncImage(
                        model = full,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = {
                            if (aspectRatio == null) {
                                onImageSize(it.painter.intrinsicSize)
                                loadedSize = it.painter.intrinsicSize
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun VideoPage(
    item: MediaItem,
    isCurrent: Boolean,
    onBack: () -> Unit,
    onChromeChange: (Boolean) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val context = LocalContext.current
    val thumbnail = remember(item.uri) {
        ImageRequest.Builder(context).data(item.uri).size(ThumbnailRequestSize).build()
    }

    val aspectRatio = remember(item.width, item.height) {
        if (item.width > 0 && item.height > 0) item.width.toFloat() / item.height.toFloat() else null
    }

    val videoModifier = remember(aspectRatio) {
        if (aspectRatio != null) {
            Modifier
                .aspectRatio(aspectRatio)
                .fillMaxWidth()
        } else {
            Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp)
        }
    }

    DragToDismissBox(
        enabled = isCurrent,
        onDismiss = onBack,
        onDragStateChange = { isDragging ->
            if (isDragging) onChromeChange(false)
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val sharedModifier = if (isCurrent && sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    videoModifier.sharedElement(
                        sharedContentState = rememberSharedContentState(key = "media_${item.id}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = { _, _ ->
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            )
                        },
                    )
                }
            } else {
                videoModifier
            }

            Box(modifier = sharedModifier) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isCurrent) {
                    VideoPlayer(
                        uri = item.uri,
                        onControllerVisibilityChange = onChromeChange,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DragToDismissBox(
    enabled: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDragStateChange: (isDragging: Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var predictiveBackProgress by remember { mutableFloatStateOf(0f) }
    var startTouchY by remember { mutableFloatStateOf(Float.NaN) }

    val density = LocalDensity.current
    val maxDragDistancePx = with(density) { 600.dp.toPx() }
    val dismissThresholdPx = with(density) { 160.dp.toPx() }
    val flingVelocityThresholdPx = with(density) { 1000.dp.toPx() }

    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnDragStateChange by rememberUpdatedState(onDragStateChange)

    PredictiveBackHandler(enabled = currentEnabled && !isDragging) { progressFlow ->
        startTouchY = Float.NaN
        try {
            progressFlow.collect { backEvent ->
                if (startTouchY.isNaN()) {
                    startTouchY = backEvent.touchY
                }
                predictiveBackProgress = backEvent.progress
                offsetY = (backEvent.touchY - startTouchY) * 0.4f
                val edgeSign = if (backEvent.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                offsetX = backEvent.progress * (maxDragDistancePx * 0.15f) * edgeSign
                currentOnDragStateChange(backEvent.progress > 0f)
            }
            currentOnDismiss()
        } catch (_: CancellationException) {
            coroutineScope.launch {
                val startX = offsetX
                val startY = offsetY
                val startProg = predictiveBackProgress
                animate(
                    initialValue = 1f,
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { fraction, _ ->
                    offsetX = startX * fraction
                    offsetY = startY * fraction
                    predictiveBackProgress = startProg * fraction
                }
                currentOnDragStateChange(false)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                val progress = ViewerDragState.calculateDragProgress(offsetY, maxDragDistancePx, predictiveBackProgress)
                val scrimAlpha = ViewerDragState.calculateScrimAlpha(progress)
                drawRect(color = Color.Black.copy(alpha = scrimAlpha))
                drawContent()
            }
            .then(
                if (currentEnabled) {
                    Modifier.pointerInput(Unit) {
                        val velocityTracker = VelocityTracker()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pointerId = down.id
                            val startPosition = down.position
                            var isSwipingVertical = false
                            var isSwipingHorizontal = false
                            velocityTracker.resetTracking()
                            velocityTracker.addPosition(down.uptimeMillis, down.position)

                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                velocityTracker.addPosition(change.uptimeMillis, change.position)

                                val dragAmount = change.position - change.previousPosition

                                if (!isSwipingVertical && !isSwipingHorizontal) {
                                    val totalDelta = change.position - startPosition
                                    val dx = abs(totalDelta.x)
                                    val dy = abs(totalDelta.y)
                                    val touchSlop = viewConfiguration.touchSlop
                                    if (dy > touchSlop && dy > dx * 1.2f) {
                                        isSwipingVertical = true
                                        isDragging = true
                                        currentOnDragStateChange(true)
                                    } else if (dx > touchSlop && dx > dy * 1.2f) {
                                        isSwipingHorizontal = true
                                    }
                                }

                                if (isSwipingVertical) {
                                    change.consume()
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                }
                            } while (event.changes.any { it.pressed })

                            if (isSwipingVertical) {
                                val velocity = velocityTracker.calculateVelocity()
                                val totalY = offsetY
                                val velocityY = velocity.y

                                isDragging = false
                                if (ViewerDragState.shouldDismissOnRelease(totalY, velocityY, dismissThresholdPx, flingVelocityThresholdPx)) {
                                    currentOnDismiss()
                                } else {
                                    coroutineScope.launch {
                                        val startX = offsetX
                                        val startY = offsetY
                                        animate(
                                            initialValue = 1f,
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow,
                                            ),
                                        ) { fraction, _ ->
                                            offsetX = startX * fraction
                                            offsetY = startY * fraction
                                        }
                                        currentOnDragStateChange(false)
                                    }
                                }
                            }
                        }
                    }
                } else Modifier
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = ViewerDragState.calculateDragProgress(offsetY, maxDragDistancePx, predictiveBackProgress)
                    translationX = offsetX * 0.85f
                    translationY = offsetY
                    val s = ViewerDragState.calculateScale(progress)
                    scaleX = s
                    scaleY = s
                    clip = progress > 0.01f
                    val cornerRadiusPx = (ViewerDragState.calculateCornerRadiusDp(progress)).dp.toPx()
                    shape = RoundedCornerShape(cornerRadiusPx)
                },
        ) {
            content()
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    uri: String,
    onControllerVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            setMediaItem(PlayerMediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                // Let the thumbnail show through until the first frame is ready.
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        onControllerVisibilityChange(visibility == View.VISIBLE)
                    },
                )
            }
        },
        update = { it.player = player },
    )
}

@Composable
private fun TopBar(item: MediaItem, onBack: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d, yyyy · h:mm a", Locale.getDefault()) }
    val title = remember(item.dateTakenMillis) {
        Instant.ofEpochMilli(item.dateTakenMillis).atZone(ZoneId.systemDefault()).format(formatter)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TopGradient)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.viewer_back),
                tint = Color.White,
            )
        }
        Text(text = title, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

private class ViewerAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
private fun ActionBar(actions: List<ViewerAction>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BottomGradient)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        actions.forEach { action ->
            IconButton(onClick = action.onClick) {
                Icon(action.icon, action.label, tint = Color.White)
            }
        }
    }
}

/** Hides the system bars while the viewer chrome is hidden; a swipe from the edge brings them back. */
@Composable
private fun ImmersiveMode(enabled: Boolean) {
    val view = LocalView.current
    val window = remember(view) { view.context.findActivity()?.window }
    val controller = remember(window, view) { window?.let { WindowCompat.getInsetsController(it, view) } }
    
    DisposableEffect(enabled, controller) {
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enabled) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private fun shareMedia(context: Context, item: MediaItem) {
    val uri = Uri.parse(item.uri)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = item.mimeType.ifEmpty { if (item.isVideo) "video/*" else "image/*" }
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

object ViewerDragState {
    fun calculateDragProgress(
        offsetYPx: Float,
        maxDragDistancePx: Float,
        predictiveBackProgress: Float = 0f,
    ): Float {
        val verticalProgress = (abs(offsetYPx) / maxDragDistancePx).coerceIn(0f, 1f)
        return max(verticalProgress, predictiveBackProgress)
    }

    fun calculateScale(dragProgress: Float): Float =
        1f - (dragProgress * 0.35f)

    fun calculateScrimAlpha(dragProgress: Float): Float =
        (1f - dragProgress * 1.2f).coerceIn(0f, 1f)

    fun calculateCornerRadiusDp(dragProgress: Float): Float =
        dragProgress * 28f

    fun shouldDismissOnRelease(
        totalYPx: Float,
        velocityYPxPerSec: Float,
        dismissThresholdPx: Float,
        flingVelocityThresholdPx: Float,
    ): Boolean {
        val absY = abs(totalYPx)
        val absVelY = abs(velocityYPxPerSec)
        return absY > dismissThresholdPx || (absY > 50f && absVelY > flingVelocityThresholdPx)
    }
}

private fun LazyPagingItems<MediaItem>.peekOrNull(index: Int): MediaItem? =
    if (index in 0 until itemCount) peek(index) else null

private fun LazyPagingItems<MediaItem>.getOrNull(index: Int): MediaItem? =
    if (index in 0 until itemCount) get(index) else null
