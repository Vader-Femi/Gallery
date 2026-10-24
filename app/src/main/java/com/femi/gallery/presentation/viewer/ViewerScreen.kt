package com.femi.gallery.presentation.viewer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
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
import com.femi.gallery.domain.model.MediaPositions
import com.femi.gallery.domain.model.toRef
import com.femi.gallery.presentation.common.GridThumbnails
import com.femi.gallery.presentation.common.findActivity
import com.femi.gallery.presentation.common.mediaSharedBounds
import com.femi.gallery.presentation.common.rememberTransitionSettled
import com.femi.gallery.presentation.locked.LockedGate
import com.femi.gallery.presentation.selection.SelectionHost
import com.femi.gallery.presentation.selection.lockWithScreenLockCheck
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.media3.common.MediaItem as PlayerMediaItem
import kotlinx.coroutines.launch

private const val Tag = "Viewer"
private const val ThumbnailRequestSize = 512
private const val FullRequestSize = 2160

@Composable
fun ViewerScreen(
    onBack: () -> Unit,
    onEdit: (uri: String) -> Unit,
    /** Reports the photo shown, so the grid behind can scroll to it when the viewer closes. */
    onViewedChange: (mediaIndex: Int, mediaId: Long) -> Unit = { _, _ -> },
    /** How far the photo has been pulled away; shared with the Back gesture, which is handled outside. */
    drag: ViewerDragState = remember { ViewerDragState() },
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    if (viewModel.isLocked) {
        // Photos from the Locked folder only show after the user authenticates.
        LockedGate(session = viewModel.lockSession, onClose = onBack) {
            ViewerContent(onBack, onEdit, onViewedChange, drag, viewModel)
        }
    } else {
        ViewerContent(onBack, onEdit, onViewedChange, drag, viewModel)
    }
}

@Composable
private fun ViewerContent(
    onBack: () -> Unit,
    onEdit: (uri: String) -> Unit,
    onViewedChange: (mediaIndex: Int, mediaId: Long) -> Unit,
    drag: ViewerDragState,
    viewModel: ViewerViewModel,
) {
    val items = viewModel.media.collectAsLazyPagingItems()
    val scope = rememberCoroutineScope()
    val backgroundColor = MaterialTheme.colorScheme.background
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
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { drag.containerHeight = it.height.toFloat() }
            .drawBehind { drawRect(backgroundColor, alpha = drag.backgroundAlpha) },
    ) {
        if (items.itemCount > 0) {
            // Open on the photo that was tapped. The tapped position alone can point at a different
            // photo, because this list was loaded separately from the grid's.
            val startPage = remember {
                val tapped = viewModel.startIndex.coerceIn(0, items.itemCount - 1)
                val found = MediaPositions.find(tapped, viewModel.mediaId, items.itemCount) { items.peekOrNull(it)?.id }
                if (found != null && found != tapped) {
                    Log.w(Tag, "Tapped photo was at $tapped in the grid but is at $found here (${found - tapped})")
                } else if (found == null && viewModel.mediaId != MediaPositions.NO_ID) {
                    Log.w(Tag, "Tapped photo ${viewModel.mediaId} not found around position $tapped; using the position")
                }
                found ?: tapped
            }
            val pagerState = rememberPagerState(initialPage = startPage) { items.itemCount }

            val settledId = items.peekOrNull(pagerState.settledPage)?.id
            LaunchedEffect(pagerState.settledPage, settledId) {
                onViewedChange(pagerState.settledPage, settledId ?: MediaPositions.NO_ID)
            }

            val current = items.peekOrNull(pagerState.currentPage)
            val activeVideoUri = if (current?.isVideo == true) current.uri else null

            var activePlayer by remember(activeVideoUri) { mutableStateOf<ExoPlayer?>(null) }
            var isPlaying by remember(activeVideoUri) { mutableStateOf(false) }
            var isMuted by remember { mutableStateOf(true) }
            var currentPositionMs by remember(activeVideoUri) { mutableStateOf(0L) }
            var durationMs by remember(activeVideoUri) { mutableStateOf(0L) }
            var isStripExpanded by remember(activeVideoUri) { mutableStateOf(false) }

            DisposableEffect(activeVideoUri) {
                if (activeVideoUri == null) {
                    activePlayer = null
                    onDispose { }
                } else {
                    val newPlayer = ExoPlayer.Builder(context).build().apply {
                        setAudioAttributes(AudioAttributes.DEFAULT, true)
                        setMediaItem(PlayerMediaItem.fromUri(activeVideoUri))
                        playWhenReady = true
                        volume = if (isMuted) 0f else 1f
                        prepare()
                    }
                    activePlayer = newPlayer

                    val listener = object : androidx.media3.common.Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                        }
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == androidx.media3.common.Player.STATE_READY) {
                                durationMs = newPlayer.duration.coerceAtLeast(0L)
                            }
                        }
                    }
                    newPlayer.addListener(listener)

                    onDispose {
                        newPlayer.removeListener(listener)
                        newPlayer.release()
                        activePlayer = null
                    }
                }
            }

            LaunchedEffect(activePlayer, isPlaying) {
                val p = activePlayer ?: return@LaunchedEffect
                while (true) {
                    currentPositionMs = p.currentPosition.coerceAtLeast(0L)
                    durationMs = p.duration.coerceAtLeast(0L)
                    kotlinx.coroutines.delay(100)
                }
            }

            // Pulling the photo down (or the Back gesture) carries it away over the live grid behind;
            // letting go past a point closes the viewer, and pulling it up opens the info.
            Box(
                Modifier
                    .fillMaxSize()
                    .viewerDrag(
                        enabled = { !drag.zoomed },
                        onDrag = drag::dragBy,
                        onEnd = { velocityY ->
                            scope.launch {
                                when (drag.release(velocityY)) {
                                    ViewerDragMath.Release.Dismiss -> onBack()
                                    ViewerDragMath.Release.ShowInfo -> {
                                        current?.let(viewModel::showDetails)
                                        drag.restore()
                                    }
                                    ViewerDragMath.Release.Restore -> drag.restore()
                                }
                            }
                        },
                        onCancel = { scope.launch { drag.restore() } },
                    ),
            ) {
                Box(Modifier.fillMaxSize().dragTransform(drag)) {
                    CompositionLocalProvider(LocalViewerDrag provides drag) {
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
                                bottomReserve = 0.dp,
                                activePlayer = if (pagerState.settledPage == page) activePlayer else null,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = chromeVisible && current != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart).graphicsLayer { alpha = drag.chromeAlpha },
            ) {
                if (current != null) TopBar(item = current, onBack = onBack)
            }

            AnimatedVisibility(
                visible = chromeVisible && current != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = drag.chromeAlpha },
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
                        else -> listOfNotNull(
                            ViewerAction(Icons.Outlined.Share, stringResource(R.string.viewer_share)) {
                                shareMedia(context, current)
                            },
                            if (current.isVideo) {
                                null
                            } else {
                                ViewerAction(Icons.Outlined.Edit, stringResource(R.string.viewer_edit)) {
                                    onEdit(current.uri)
                                }
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
                    Column {
                        if (current.isVideo && activePlayer != null) {
                            VideoControlsOverlay(
                                isPlaying = isPlaying,
                                currentPositionMs = currentPositionMs,
                                durationMs = durationMs,
                                isMuted = isMuted,
                                onTogglePlayPause = {
                                    val p = activePlayer ?: return@VideoControlsOverlay
                                    if (p.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                                        p.seekTo(0)
                                        p.play()
                                    } else if (isPlaying) {
                                        p.pause()
                                    } else {
                                        p.play()
                                    }
                                },
                                onToggleMute = {
                                    isMuted = !isMuted
                                    activePlayer?.volume = if (isMuted) 0f else 1f
                                },
                            )
                        }
                        if (items.itemCount > 1) {
                            if (current.isVideo && isStripExpanded) {
                                ExpandedVideoSeekBar(
                                    item = current,
                                    currentPositionMs = currentPositionMs,
                                    durationMs = durationMs,
                                    onSeek = { seekMs -> activePlayer?.seekTo(seekMs) },
                                )
                            } else {
                                MediaStrip(
                                    items = items,
                                    pagerState = pagerState,
                                    onVideoItemTap = {
                                        isStripExpanded = !isStripExpanded
                                    },
                                )
                            }
                        }
                        ActionBar(actions = cachedActions)
                    }
                }
            }
        }
    }

    details?.let { DetailsSheet(details = it, onDismiss = viewModel::hideDetails) }
}

@Composable
private fun ViewerPage(
    item: MediaItem?,
    isCurrent: Boolean,
    chromeVisible: Boolean,
    onChromeChange: (Boolean) -> Unit,
    /** Room to keep free at the bottom, for the strip. */
    bottomReserve: Dp,
    activePlayer: ExoPlayer? = null,
) {
    when {
        item == null -> Box(Modifier.fillMaxSize())
        item.isVideo -> VideoPage(item, isCurrent, onTap = { onChromeChange(!chromeVisible) }, activePlayer = activePlayer)
        else -> PhotoPage(item, isCurrent, onTap = { onChromeChange(!chromeVisible) })
    }
}

@Composable
internal fun PhotoPage(item: MediaItem, isCurrent: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    // Layer 1: the system thumbnail appears instantly. Layer 2: the sharper image fades in on top.
    val thumbnail = remember(item.uri) { thumbnailRequest(context, item) }
    val full = remember(item.uri) {
        ImageRequest.Builder(context).data(item.uri).size(FullRequestSize).crossfade(true).build()
    }
    val handleTap = remember(onTap) { { onTap() } }
    // The current photo grows out of (and shrinks back into) its thumbnail in the grid.
    val sharedModifier = if (isCurrent) Modifier.mediaSharedBounds(item.id) else Modifier
    ZoomableBox(isCurrent = isCurrent, onTap = handleTap, modifier = sharedModifier) { onImageSize ->
        AsyncImage(
            model = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { onImageSize(it.painter.intrinsicSize) },
        )
        AsyncImage(
            model = full,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { onImageSize(it.painter.intrinsicSize) },
        )
    }
}

@Composable
internal fun VideoPage(
    item: MediaItem,
    isCurrent: Boolean,
    onTap: () -> Unit,
    activePlayer: ExoPlayer? = null,
) {
    val context = LocalContext.current
    val thumbnail = remember(item.uri) { thumbnailRequest(context, item) }
    val sharedModifier = if (isCurrent) Modifier.mediaSharedBounds(item.id) else Modifier
    val settled = rememberTransitionSettled()

    ZoomableBox(
        isCurrent = isCurrent,
        onTap = onTap,
        modifier = sharedModifier,
    ) { onImageSize ->
        LaunchedEffect(item.width, item.height) {
            if (item.width > 0 && item.height > 0) {
                onImageSize(Size(item.width.toFloat(), item.height.toFloat()))
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { onImageSize(it.painter.intrinsicSize) },
            )
            if (isCurrent && settled) {
                VideoPlayer(
                    uri = item.uri,
                    activePlayer = activePlayer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    uri: String,
    activePlayer: ExoPlayer? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val fallbackPlayer = remember(uri, activePlayer) {
        if (activePlayer != null) null
        else ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            setMediaItem(PlayerMediaItem.fromUri(uri))
            playWhenReady = true
            prepare()
        }
    }
    val player = activePlayer ?: fallbackPlayer!!

    DisposableEffect(fallbackPlayer) {
        onDispose { fallbackPlayer?.release() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.viewer_player_view, null, false) as PlayerView).apply {
                useController = false
                this.player = player
            }
        },
        update = {
            it.useController = false
            it.player = player
        },
    )
}

@Composable
private fun VideoControlsOverlay(
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    isMuted: Boolean,
    onTogglePlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerBg = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
    val contentColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Bottom Center: Capsule with play/pause and time
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .background(containerBg, RoundedCornerShape(50))
                .clickable(onClick = onTogglePlayPause)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "${formatVideoTime(currentPositionMs)} / ${formatVideoTime(durationMs)}",
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        // Bottom Right: Mute toggle
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(40.dp)
                .background(containerBg, CircleShape),
        ) {
            Icon(
                imageVector = if (isMuted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The image shown at once while the real one loads: what the grid already has in memory, if anything. */
private fun thumbnailRequest(context: Context, item: MediaItem): ImageRequest =
    ImageRequest.Builder(context)
        .data(item.uri)
        .size(ThumbnailRequestSize)
        .placeholderMemoryCacheKey(GridThumbnails.keyFor(item.id))
        .build()

@Composable
private fun TopBar(item: MediaItem, onBack: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d, yyyy · h:mm a", Locale.getDefault()) }
    val title = remember(item.dateTakenMillis) {
        Instant.ofEpochMilli(item.dateTakenMillis).atZone(ZoneId.systemDefault()).format(formatter)
    }
    val surface = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.viewer_back),
                tint = contentColor,
            )
        }
        Text(text = title, color = contentColor, style = MaterialTheme.typography.titleMedium)
    }
}

private class ViewerAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
private fun ActionBar(actions: List<ViewerAction>) {
    val surface = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        actions.forEach { action ->
            IconButton(onClick = action.onClick) {
                Icon(action.icon, action.label, tint = contentColor)
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
    val isDark = isSystemInDarkTheme()

    DisposableEffect(enabled, controller, isDark) {
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.isAppearanceLightStatusBars = !isDark
        controller?.isAppearanceLightNavigationBars = !isDark
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

internal fun LazyPagingItems<MediaItem>.peekOrNull(index: Int): MediaItem? =
    if (index in 0 until itemCount) peek(index) else null

private fun LazyPagingItems<MediaItem>.getOrNull(index: Int): MediaItem? =
    if (index in 0 until itemCount) get(index) else null
