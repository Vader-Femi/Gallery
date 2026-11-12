package com.femi.gallery.presentation.viewer

import android.app.WallpaperManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.femi.gallery.presentation.common.shareUris
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.zIndex
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
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
import com.femi.gallery.playback.PlayerFactory
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaPositions
import com.femi.gallery.domain.model.toRef
import com.femi.gallery.presentation.common.BubbleMorphOverlayClip
import com.femi.gallery.presentation.common.GridThumbnails
import com.femi.gallery.presentation.common.findActivity
import com.femi.gallery.presentation.common.mediaSharedBounds
import com.femi.gallery.presentation.common.renderAboveSharedElements
import com.femi.gallery.presentation.common.rememberTransitionSettled
import com.femi.gallery.presentation.locked.LockedGate
import com.femi.gallery.presentation.selection.SelectionHost
import com.femi.gallery.presentation.selection.lockWithScreenLockCheck
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.media3.common.MediaItem as PlayerMediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val Tag = "Viewer"
private const val ThumbnailRequestSize = 512

@Composable
fun ViewerScreen(
    onBack: () -> Unit,
    onEdit: (uri: String) -> Unit,
    onTrim: (uri: String) -> Unit,
    /** Reports the photo shown, so the grid behind can scroll to it when the viewer closes. */
    onViewedChange: (mediaIndex: Int, mediaId: Long) -> Unit = { _, _ -> },
    /** How far the photo has been pulled away; shared with the Back gesture, which is handled outside. */
    drag: ViewerDragState = remember { ViewerDragState() },
    isMapOrigin: Boolean = false,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    if (viewModel.isLocked) {
        // Photos from the Locked folder only show after the user authenticates.
        LockedGate(session = viewModel.lockSession, onClose = onBack) {
            ViewerContent(onBack, onEdit, onTrim, onViewedChange, drag, isMapOrigin, viewModel)
        }
    } else {
        ViewerContent(onBack, onEdit, onTrim, onViewedChange, drag, isMapOrigin, viewModel)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ViewerContent(
    onBack: () -> Unit,
    onEdit: (uri: String) -> Unit,
    onTrim: (uri: String) -> Unit,
    onViewedChange: (mediaIndex: Int, mediaId: Long) -> Unit,
    drag: ViewerDragState,
    isMapOrigin: Boolean,
    viewModel: ViewerViewModel,
) {
    val items = viewModel.media.collectAsLazyPagingItems()
    val scope = rememberCoroutineScope()
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val isExpanded = with(density) { windowInfo.containerSize.width.toDp() } >= 840.dp

    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    val backgroundColor = if (chromeVisible) MaterialTheme.colorScheme.background else Color.Black
    val details by viewModel.details.collectAsStateWithLifecycle()
    val currentHasText by viewModel.currentHasText.collectAsStateWithLifecycle()
    val canRevert by viewModel.canRevert.collectAsStateWithLifecycle()
    val imageRevision by viewModel.imageRevision.collectAsStateWithLifecycle()
    val scrollToText by viewModel.scrollToText.collectAsStateWithLifecycle()
    val motionPhoto by viewModel.motionPhoto.collectAsStateWithLifecycle()
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
            var isMotionPlaying by remember(current?.id) { mutableStateOf(false) }
            LaunchedEffect(current?.id) {
                viewModel.checkTextFor(current)
            }

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

            val autoPlayDefault by viewModel.autoplayVideos.collectAsStateWithLifecycle()
            val muteDefault by viewModel.muteVideos.collectAsStateWithLifecycle()

            var activePlayer by remember(activeVideoUri) { mutableStateOf<ExoPlayer?>(null) }
            var isPlaying by remember(activeVideoUri) { mutableStateOf(autoPlayDefault) }
            var isMuted by rememberSaveable(activeVideoUri) { mutableStateOf(muteDefault) }
            var currentPositionMs by remember(activeVideoUri) { mutableLongStateOf(0L) }
            var durationMs by remember(activeVideoUri) { mutableLongStateOf(0L) }
            var isVideoTrackUnsupported by remember(activeVideoUri) { mutableStateOf(false) }

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
                    val autoPlay = autoPlayDefault
                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build()
                    val newPlayer = playerHolder.acquire(!isMuted).apply {
                        setAudioAttributes(audioAttributes, !isMuted)
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
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            val isDecoderOrFormatError = error.errorCode in listOf(
                                androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                                androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                                androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED,
                            )
                            val sampleMimeType = (error as? ExoPlaybackException)?.rendererFormat?.sampleMimeType
                            val isVideoFailure = (sampleMimeType?.startsWith("video/") == true) ||
                                (isDecoderOrFormatError && !newPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO))

                            if (isVideoFailure && !isVideoTrackUnsupported) {
                                isVideoTrackUnsupported = true
                                newPlayer.trackSelectionParameters = newPlayer.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                                    .build()
                                newPlayer.prepare()
                                if (autoPlay) {
                                    newPlayer.play()
                                }
                            }
                        }
                    }
                    newPlayer.addListener(listener)

                    onDispose {
                        newPlayer.removeListener(listener)
                        newPlayer.stop()
                        newPlayer.clearMediaItems()
                        if (newPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO)) {
                            newPlayer.trackSelectionParameters = newPlayer.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                                .build()
                        }
                        activePlayer = null
                    }
                }
            }

            LaunchedEffect(isMuted) {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build()
                activePlayer?.setAudioAttributes(audioAttributes, !isMuted)
                activePlayer?.volume = if (isMuted) 0f else 1f
            }

            // Hook up volume up button to unmute automatically when a video is muted.
            DisposableEffect(activePlayer, isMuted) {
                val act = context.findActivity() as? MainActivity
                if (act != null && isMuted) {
                    act.viewerVolumeHandler = {
                        isMuted = false
                        activePlayer?.volume = 1f
                        if (!isPlaying && autoPlayDefault) {
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
                        enabled = { !drag.zoomed && fullPlayerUri == null },
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
                            val isCurrent = pagerState.settledPage == page
                            ViewerPage(
                                item = pageItem,
                                isCurrent = isCurrent,
                                chromeVisible = chromeVisible,
                                onChromeChange = handleChromeChange,
                                bottomReserve = 0.dp,
                                activePlayer = if (isCurrent && pageItem?.uri == activeVideoUri) activePlayer else null,
                                playerProvided = true,
                                imageRevision = imageRevision,
                                isMapOrigin = isMapOrigin,
                                motionVideoUri = if (isCurrent) motionPhoto?.videoUri else null,
                                isMotionPlaying = isCurrent && isMotionPlaying,
                                isVideoTrackUnsupported = isCurrent && pageItem?.uri == activeVideoUri && isVideoTrackUnsupported,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = chromeVisible && current != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .renderAboveSharedElements(zIndex = 1f)
                    .graphicsLayer { alpha = drag.chromeAlpha },
            ) {
                if (current != null) TopBar(item = current, onBack = onBack)
            }

            AnimatedVisibility(
                visible = chromeVisible && current != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .renderAboveSharedElements(zIndex = 1f)
                    .graphicsLayer { alpha = drag.chromeAlpha },
            ) {
                if (current != null) {
                    val ref = current.toRef()
                    val actions = when {
                        viewModel.isRecycleBin -> {
                            val primary = listOfNotNull(
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
                            primary to emptyList<ViewerAction>()
                        }
                        viewModel.isLocked -> {
                            val primary = listOfNotNull(
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
                            primary to emptyList<ViewerAction>()
                        }
                        else -> {
                            val primary = listOfNotNull(
                                ViewerAction(Icons.Outlined.Share, stringResource(R.string.viewer_share)) {
                                    shareMedia(context, current)
                                },
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
                                ViewerAction(Icons.Outlined.Info, stringResource(R.string.viewer_info)) {
                                    viewModel.showDetails(current)
                                },
                            )
                            val overflow = listOfNotNull(
                                ViewerAction(
                                    icon = if (current.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    label = stringResource(if (current.isFavorite) R.string.action_unfavorite else R.string.action_favorite),
                                ) { viewModel.actions.toggleFavorite(listOf(ref)) },
                                if (current.isVideo) {
                                    ViewerAction(Icons.Outlined.Edit, stringResource(R.string.viewer_edit)) {
                                        onTrim(current.uri)
                                    }
                                } else null,
                                if (!current.isVideo && canRevert) {
                                    ViewerAction(Icons.Outlined.History, stringResource(R.string.action_revert_original)) {
                                        viewModel.revertOriginal(current) { success ->
                                            if (success) {
                                                Toast.makeText(context, R.string.toast_reverted, Toast.LENGTH_SHORT).show()
                                                items.refresh()
                                            } else {
                                                Toast.makeText(context, R.string.toast_failed, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                } else null,
                                if (!current.isVideo) {
                                    ViewerAction(Icons.Outlined.Wallpaper, stringResource(R.string.action_set_as_wallpaper)) {
                                        setWallpaper(context, current)
                                    }
                                } else null,
                                ViewerAction(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy_text)) {
                                    viewModel.copyPhotoText(current) { text ->
                                        if (!text.isNullOrBlank()) {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Media text", text))
                                            Toast.makeText(context, R.string.text_copied, Toast.LENGTH_SHORT).show()
                                        } else {
                                            val msg = if (current.isVideo) R.string.text_none_found_video else R.string.text_none_found
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                ViewerAction(Icons.Outlined.Lock, stringResource(R.string.action_lock)) {
                                    lockWithScreenLockCheck(context) { viewModel.actions.lock(listOf(ref)) }
                                },
                            )
                            primary to overflow
                        }
                    }
                    val cachedPrimary = remember(ref, viewModel.isRecycleBin, viewModel.isLocked, current.isFavorite) { actions.first }
                    val cachedOverflow = remember(ref, viewModel.isRecycleBin, viewModel.isLocked, current.isFavorite, canRevert) { actions.second }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = motionPhoto != null && !current.isVideo,
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut(),
                                modifier = Modifier.align(Alignment.Center),
                            ) {
                                Surface(
                                    onClick = { isMotionPlaying = !isMotionPlaying },
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    shadowElevation = 4.dp,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (isMotionPlaying) Icons.Filled.Pause else Icons.Outlined.PlayCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(
                                                if (isMotionPlaying) R.string.viewer_motion_photo_pause else R.string.viewer_motion_photo
                                            ),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = currentHasText,
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut(),
                                modifier = Modifier.align(Alignment.CenterEnd),
                            ) {
                                Surface(
                                    onClick = { viewModel.showDetails(current, scrollToText = true) },
                                    shape = CircleShape,
                                    color = Color(0xFFFFD600),
                                    shadowElevation = 4.dp,
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "T",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = Color(0xFF1C1B1F),
                                        )
                                    }
                                }
                            }
                        }
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
                            key(imageRevision) {
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
                        }
                        ActionBar(primaryActions = cachedPrimary, overflowActions = cachedOverflow)
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

            if (isExpanded) {
                AnimatedVisibility(
                    visible = details != null,
                    enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .systemBarsPadding()
                        .zIndex(3f),
                ) {
                    details?.let {
                        DetailsPanel(
                            details = it,
                            onClose = viewModel::hideDetails,
                            scrollToBottom = scrollToText,
                        )
                    }
                }
            }
        }
    }

    if (!isExpanded) {
        details?.let {
            DetailsSheet(
                details = it,
                onDismiss = viewModel::hideDetails,
                scrollToBottom = scrollToText,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
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
    imageRevision: Long = 0L,
    isMapOrigin: Boolean = false,
    motionVideoUri: String? = null,
    isMotionPlaying: Boolean = false,
    isVideoTrackUnsupported: Boolean = false,
) {
    val clipInOverlay = if (isMapOrigin) BubbleMorphOverlayClip else null
    when {
        item == null -> Box(Modifier.fillMaxSize())
        item.isVideo -> VideoPage(
            item,
            isCurrent,
            onTap = { onChromeChange(!chromeVisible) },
            activePlayer = activePlayer,
            ownsPlayer = !playerProvided,
            clipInOverlay = clipInOverlay,
            isVideoTrackUnsupported = isVideoTrackUnsupported,
        )
        else -> PhotoPage(
            item = item,
            isCurrent = isCurrent,
            imageRevision = imageRevision,
            clipInOverlay = clipInOverlay,
            motionVideoUri = motionVideoUri,
            isMotionPlaying = isMotionPlaying,
            onTap = { onChromeChange(!chromeVisible) },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun PhotoPage(
    item: MediaItem,
    isCurrent: Boolean,
    imageRevision: Long = 0L,
    clipInOverlay: SharedTransitionScope.OverlayClip? = null,
    motionVideoUri: String? = null,
    isMotionPlaying: Boolean = false,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val latestOnTap by rememberUpdatedState(onTap)
    val sharedModifier = if (isCurrent) Modifier.mediaSharedBounds(item.id, clipInOverlay = clipInOverlay) else Modifier

    val viewerDrag = LocalViewerDrag.current

    Box(modifier = Modifier.fillMaxSize()) {
        key(item.uri, imageRevision) {
            val zoomableState = me.saket.telephoto.zoomable.rememberZoomableImageState()
            // A photo that is swiped away is put back to fit-to-screen, as the photo page did before Telephoto, so that
            // coming back to it does not show it still zoomed in.
            LaunchedEffect(isCurrent) {
                if (!isCurrent) zoomableState.zoomableState.resetZoom(animationSpec = snap())
            }
            LaunchedEffect(zoomableState.zoomableState.zoomFraction, isCurrent) {
                if (viewerDrag != null && isCurrent) {
                    viewerDrag.zoomed = (zoomableState.zoomableState.zoomFraction ?: 0f) > 0.02f
                }
            }

            val thumbnailKey = GridThumbnails.keyFor(item.id)
            val request = remember(item.uri, thumbnailKey, imageRevision) {
                ImageRequest.Builder(context)
                    .data(item.uri)
                    .apply { if (thumbnailKey != null && imageRevision == 0L) placeholderMemoryCacheKey(thumbnailKey) }
                    .memoryCacheKey(if (imageRevision > 0L) "${item.uri}?rev=$imageRevision" else null)
                    .crossfade(true)
                    .build()
            }

            me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage(
                model = request,
                state = zoomableState,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().then(sharedModifier),
                onClick = { latestOnTap() }
            )
        }

        if (isCurrent && isMotionPlaying && motionVideoUri != null) {
            MotionVideoPlayer(
                videoUri = motionVideoUri,
                onTap = { latestOnTap() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun MotionVideoPlayer(
    videoUri: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val motionPlayer = remember(videoUri) {
        PlayerFactory.create(context).apply {
            setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            setMediaItem(PlayerMediaItem.fromUri(videoUri))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(motionPlayer) {
        onDispose {
            motionPlayer.stop()
            motionPlayer.clearMediaItems()
            motionPlayer.release()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        motionPlayer.pause()
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.viewer_player_view, null, false) as PlayerView).apply {
                    useController = false
                    this.player = motionPlayer
                }
            },
            update = {
                it.useController = false
                it.player = motionPlayer
            },
            onRelease = { it.player = null },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTap,
                )
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun VideoPage(
    item: MediaItem,
    isCurrent: Boolean,
    onTap: () -> Unit,
    activePlayer: ExoPlayer? = null,
    /** Whether the page may make a player of its own when none is given (the external viewer relies on that). */
    ownsPlayer: Boolean = true,
    clipInOverlay: SharedTransitionScope.OverlayClip? = null,
    isVideoTrackUnsupported: Boolean = false,
) {
    val context = LocalContext.current
    val thumbnail = remember(item.uri) { thumbnailRequest(context, item) }
    val sharedModifier = if (isCurrent) Modifier.mediaSharedBounds(item.id, clipInOverlay = clipInOverlay) else Modifier
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
            if (isCurrent && isVideoTrackUnsupported) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.player_audio_only_notice),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
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
        else {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()
            PlayerFactory.create(context).apply {
                setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
                setMediaItem(PlayerMediaItem.fromUri(uri))
                playWhenReady = false
                prepare()
            }
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
private fun ActionBar(primaryActions: List<ViewerAction>, overflowActions: List<ViewerAction> = emptyList()) {
    val surface = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface
    var moreExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        primaryActions.forEach { action ->
            IconButton(onClick = action.onClick) {
                Icon(action.icon, action.label, tint = contentColor)
            }
        }
        
        if (overflowActions.isNotEmpty()) {
            Box {
                IconButton(onClick = { moreExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more), tint = contentColor)
                }
                DropdownMenu(
                    expanded = moreExpanded,
                    onDismissRequest = { moreExpanded = false }
                ) {
                    overflowActions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            onClick = {
                                moreExpanded = false
                                action.onClick()
                            },
                            leadingIcon = { Icon(action.icon, contentDescription = null) }
                        )
                    }
                }
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
    shareUris(context, listOf(uri), item.mimeType.ifEmpty { if (item.isVideo) "video/*" else "image/*" })
}

private fun setWallpaper(context: Context, item: MediaItem) {
    val uri = Uri.parse(item.uri)
    val mime = item.mimeType.ifEmpty { "image/*" }
    val intent = Intent(WallpaperManager.ACTION_CROP_AND_SET_WALLPAPER).apply {
        setDataAndType(uri, mime)
        putExtra("mimeType", mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        val fallback = Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(uri, mime)
            putExtra("mimeType", mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(fallback, context.getString(R.string.action_set_as_wallpaper)))
        } catch (_: Exception) {
        }
    }
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

    fun acquire(handleFocus: Boolean): ExoPlayer = player ?: PlayerFactory.create(context)
        .apply { setAudioAttributes(AudioAttributes.DEFAULT, handleFocus) }
        .also { player = it }

    fun release() {
        player?.release()
        player = null
    }
}
