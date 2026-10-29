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
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.geometry.isSpecified
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
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.femi.gallery.R
import com.femi.gallery.MainActivity
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
import com.femi.gallery.presentation.settings.ThemePreferences
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.media3.common.MediaItem as PlayerMediaItem
import kotlinx.coroutines.delay
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
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    val backgroundColor = if (chromeVisible) MaterialTheme.colorScheme.background else Color.Black
    val details by viewModel.details.collectAsStateWithLifecycle()
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

            // A video is only prepared once the pager has rested on it for a moment. Swiping quickly past
            // several videos therefore prepares none of them (preparing one is heavy work on the main thread).
            val settledItem = items.peekOrNull(pagerState.settledPage)
            var activeVideoUri by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(pagerState.settledPage, settledItem?.id) {
                val uri = settledItem?.takeIf { it.isVideo }?.uri
                if (activeVideoUri != uri) activeVideoUri = null // let go of the previous video at once
                if (uri != null) {
                    delay(VideoSettleDelayMs)
                    activeVideoUri = uri
                }
            }

            // One player for the whole visit: creating a player is far more expensive than pointing one at a new video.
            val playerHolder = remember { SharedPlayer(context) }
            DisposableEffect(Unit) { onDispose { playerHolder.release() } }

            val autoPlayDefault = remember { ThemePreferences.getAutoPlayVideos(context) }
            val muteDefault = remember { ThemePreferences.getMuteVideos(context) }

            var activePlayer by remember(activeVideoUri) { mutableStateOf<ExoPlayer?>(null) }
            var isPlaying by remember(activeVideoUri) { mutableStateOf(autoPlayDefault) }
            var isMuted by rememberSaveable(activeVideoUri) { mutableStateOf(muteDefault) }
            var currentPositionMs by remember(activeVideoUri) { mutableLongStateOf(0L) }
            var durationMs by remember(activeVideoUri) { mutableLongStateOf(0L) }

            // The full-screen player (the Play action). It opens above the viewer and carries on from where the small one was.
            var fullPlayerUri by rememberSaveable { mutableStateOf<String?>(null) }
            var fullPlayerStartMs by rememberSaveable { mutableLongStateOf(0L) }
            val playFull: (String) -> Unit = { uri ->
                fullPlayerStartMs = currentPositionMs
                activePlayer?.pause()
                fullPlayerUri = uri
            }

            DisposableEffect(activeVideoUri) {
                if (activeVideoUri == null) {
                    activePlayer = null
                    onDispose { }
                } else {
                    val autoPlay = ThemePreferences.getAutoPlayVideos(context)
                    val newPlayer = playerHolder.acquire(!isMuted).apply {
                        setAudioAttributes(AudioAttributes.DEFAULT, !isMuted)
                        setMediaItem(PlayerMediaItem.fromUri(activeVideoUri!!))
                        playWhenReady = autoPlay
                        volume = if (isMuted) 0f else 1f
                        prepare()
                    }
                    activePlayer = newPlayer
                    isPlaying = autoPlay

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
                        newPlayer.stop()
                        newPlayer.clearMediaItems()
                        activePlayer = null
                    }
                }
            }

            LaunchedEffect(isMuted) {
                activePlayer?.setAudioAttributes(AudioAttributes.DEFAULT, !isMuted)
                activePlayer?.volume = if (isMuted) 0f else 1f
            }

            // Hook up volume up button to unmute automatically when a video is muted.
            DisposableEffect(activePlayer, isMuted) {
                val act = context.findActivity() as? MainActivity
                if (act != null && isMuted) {
                    act.viewerVolumeHandler = {
                        isMuted = false
                        activePlayer?.volume = 1f
                        if (!isPlaying && ThemePreferences.getAutoPlayVideos(context)) {
                            activePlayer?.play()
                        }
                    }
                } else {
                    act?.viewerVolumeHandler = null
                }
                onDispose {
                    act?.viewerVolumeHandler = null
                }
            }

            // The position only moves while playing; when paused it is read once (a seek sets it directly).
            LaunchedEffect(activePlayer, isPlaying) {
                val p = activePlayer ?: return@LaunchedEffect
                while (true) {
                    currentPositionMs = p.currentPosition.coerceAtLeast(0L)
                    durationMs = p.duration.coerceAtLeast(0L)
                    if (!isPlaying) break
                    delay(PositionPollMs)
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
                            val pageItem = items.getOrNull(page)
                            ViewerPage(
                                item = pageItem,
                                isCurrent = pagerState.settledPage == page,
                                chromeVisible = chromeVisible,
                                onChromeChange = handleChromeChange,
                                bottomReserve = 0.dp,
                                activePlayer = if (pagerState.settledPage == page && pageItem?.uri == activeVideoUri) activePlayer else null,
                                playerProvided = true,
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
                        viewModel.isRecycleBin -> listOfNotNull(
                            ViewerAction(Icons.Outlined.RestoreFromTrash, stringResource(R.string.action_restore)) {
                                viewModel.actions.restore(listOf(ref))
                            },
                            ViewerAction(Icons.Outlined.DeleteForever, stringResource(R.string.action_delete_forever)) {
                                viewModel.actions.deleteForever(listOf(ref))
                            },
                            if (current.isVideo) {
                                ViewerAction(Icons.Outlined.PlayCircle, stringResource(R.string.viewer_play)) {
                                    playFull(current.uri)
                                }
                            } else null,
                            ViewerAction(Icons.Outlined.Info, stringResource(R.string.viewer_info)) {
                                viewModel.showDetails(current)
                            },
                        )
                        viewModel.isLocked -> listOfNotNull(
                            ViewerAction(Icons.Outlined.LockOpen, stringResource(R.string.action_unlock)) {
                                viewModel.actions.unlock(listOf(ref))
                            },
                            ViewerAction(Icons.Outlined.Delete, stringResource(R.string.action_delete)) {
                                viewModel.actions.deleteLocked(listOf(ref))
                            },
                            if (current.isVideo) {
                                ViewerAction(Icons.Outlined.PlayCircle, stringResource(R.string.viewer_play)) {
                                    playFull(current.uri)
                                }
                            } else null,
                            ViewerAction(Icons.Outlined.Info, stringResource(R.string.viewer_info)) {
                                viewModel.showDetails(current)
                            },
                        )
                        else -> listOfNotNull(
                            ViewerAction(Icons.Outlined.Share, stringResource(R.string.viewer_share)) {
                                shareMedia(context, current)
                            },
                            ViewerAction(
                                icon = if (current.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                label = stringResource(if (current.isFavorite) R.string.action_unfavorite else R.string.action_favorite),
                            ) { viewModel.actions.toggleFavorite(listOf(ref)) },
                            if (current.isVideo) {
                                ViewerAction(Icons.Outlined.PlayCircle, stringResource(R.string.viewer_play)) {
                                    playFull(current.uri)
                                }
                            } else {
                                ViewerAction(Icons.Outlined.Edit, stringResource(R.string.viewer_edit)) {
                                    onEdit(current.uri)
                                }
                            },
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
                                currentPositionMs = { currentPositionMs },
                                durationMs = { durationMs },
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
                                    if (!isPlaying) activePlayer?.play()
                                },
                            )
                        }
                        if (items.itemCount > 1) {
                            MediaStrip(
                                items = items,
                                pagerState = pagerState,
                                currentPositionMs = { currentPositionMs },
                                durationMs = { durationMs },
                                onSeek = { seekMs ->
                                    activePlayer?.seekTo(seekMs)
                                    currentPositionMs = seekMs
                                },
                            )
                        }
                        ActionBar(actions = cachedActions)
                    }
                }
            }

            fullPlayerUri?.let { uri ->
                FullVideoPlayer(
                    uri = uri,
                    startPositionMs = fullPlayerStartMs,
                    onClose = {
                        fullPlayerUri = null
                        chromeVisible = true // the player gives the system bars back, so the viewer shows its own bars too
                    },
                )
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
    /** True when the caller supplies the player (the viewer does, once it has prepared one); no page then makes its own. */
    playerProvided: Boolean = false,
) {
    when {
        item == null -> Box(Modifier.fillMaxSize())
        item.isVideo -> VideoPage(item, isCurrent, onTap = { onChromeChange(!chromeVisible) }, activePlayer = activePlayer, ownsPlayer = !playerProvided)
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
    ZoomableBox(
        isCurrent = isCurrent,
        onTap = handleTap,
        sharedBounds = sharedModifier,
        initialImageSize = GridThumbnails.sizeFor(item.id) ?: Size.Zero,
    ) { onImageSize ->
        AsyncImage(
            model = thumbnail,
            onLoading = { state -> state.painter?.intrinsicSize?.takeKnown()?.let(onImageSize) },
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
    /** Whether the page may make a player of its own when none is given (the external viewer relies on that). */
    ownsPlayer: Boolean = true,
) {
    val context = LocalContext.current
    val thumbnail = remember(item.uri) { thumbnailRequest(context, item) }
    val sharedModifier = if (isCurrent) Modifier.mediaSharedBounds(item.id) else Modifier
    val settled = rememberTransitionSettled()

    ZoomableBox(
        isCurrent = isCurrent,
        onTap = onTap,
        sharedBounds = sharedModifier,
        initialImageSize = GridThumbnails.sizeFor(item.id) ?: Size.Zero,
    ) { onImageSize ->
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = thumbnail,
                onLoading = { state -> state.painter?.intrinsicSize?.takeKnown()?.let(onImageSize) },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { onImageSize(it.painter.intrinsicSize) },
            )
            // With a player supplied by the viewer, wait for it: building a second one here is wasted work.
            if (isCurrent && settled && (ownsPlayer || activePlayer != null)) {
                VideoPlayer(
                    uri = item.uri,
                    activePlayer = activePlayer,
                    onVideoSizeChanged = onImageSize,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    modifier: Modifier = Modifier,
    uri: String,
    activePlayer: ExoPlayer? = null,
    onVideoSizeChanged: ((Size) -> Unit)? = null,
) {
    val context = LocalContext.current
    val fallbackPlayer = remember(uri, activePlayer) {
        if (activePlayer != null) null
        else ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            setMediaItem(PlayerMediaItem.fromUri(uri))
            playWhenReady = false
            prepare()
        }
    }
    val player = activePlayer ?: fallbackPlayer!!

    DisposableEffect(fallbackPlayer) {
        onDispose { fallbackPlayer?.release() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }

    DisposableEffect(player, onVideoSizeChanged) {
        if (onVideoSizeChanged == null) return@DisposableEffect onDispose { }
        @Suppress("DEPRECATION")
        val reportSize = { videoSize: androidx.media3.common.VideoSize ->
            if (videoSize.width > 0 && videoSize.height > 0) {
                val rotated = videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270
                val w = if (rotated) videoSize.height else videoSize.width
                val h = if (rotated) videoSize.width else videoSize.height
                onVideoSizeChanged(Size(w.toFloat(), h.toFloat()))
            }
        }
        reportSize(player.videoSize)
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                reportSize(videoSize)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

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
        onRelease = { it.player = null }, // the player outlives this view
    )
}

@Composable
private fun VideoControlsOverlay(
    isPlaying: Boolean,
    /** Read where it is shown, so the clock ticking does not recompose everything around it. */
    currentPositionMs: () -> Long,
    durationMs: () -> Long,
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
                contentDescription = stringResource(if (isPlaying) R.string.viewer_pause else R.string.viewer_play),
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "${formatVideoTime(currentPositionMs())} / ${formatVideoTime(durationMs())}",
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
                contentDescription = stringResource(if (isMuted) R.string.viewer_unmute else R.string.viewer_mute),
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** This size if it is a real one: the placeholder of an image that is still loading can be empty. */
private fun Size.takeKnown(): Size? = takeIf { isSpecified && width > 0f && height > 0f }

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

/** How long the pager must rest on a video before it is prepared. */
private const val VideoSettleDelayMs = 250L

/** How often the position shown on the video controls is refreshed while playing. */
private const val PositionPollMs = 100L

/** The viewer's video player, made the first time a video needs it and released with the viewer. */
@OptIn(UnstableApi::class)
private class SharedPlayer(private val context: Context) {
    private var player: ExoPlayer? = null

    fun acquire(handleFocus: Boolean): ExoPlayer = player ?: ExoPlayer.Builder(context).build()
        .apply { setAudioAttributes(AudioAttributes.DEFAULT, handleFocus) }
        .also { player = it }

    fun release() {
        player?.release()
        player = null
    }
}
