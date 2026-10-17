package com.femi.gallery.presentation.viewer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.femi.gallery.presentation.selection.WriteRequestHandler
import com.femi.gallery.presentation.selection.lockWithScreenLockCheck
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.media3.common.MediaItem as PlayerMediaItem

private const val ThumbnailRequestSize = 512
private const val FullRequestSize = 2160

@Composable
fun ViewerScreen(
    onBack: () -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    if (viewModel.isLocked) {
        // Photos from the Locked folder only show after the user authenticates.
        LockedGate(session = viewModel.lockSession, onClose = onBack) {
            ViewerContent(onBack, viewModel)
        }
    } else {
        ViewerContent(onBack, viewModel)
    }
}

@Composable
private fun ViewerContent(
    onBack: () -> Unit,
    viewModel: ViewerViewModel,
) {
    val items = viewModel.media.collectAsLazyPagingItems()
    val details by viewModel.details.collectAsStateWithLifecycle()
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current

    ImmersiveMode(enabled = !chromeVisible)
    WriteRequestHandler(batches = viewModel.actions.batches, onSuccess = {})

    // Leave the viewer once the last photo has been moved to the trash.
    LaunchedEffect(items.loadState.refresh, items.itemCount) {
        if (items.loadState.refresh is LoadState.NotLoading && items.itemCount == 0) onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (items.itemCount > 0) {
            val pagerState = rememberPagerState(
                initialPage = viewModel.startIndex.coerceIn(0, items.itemCount - 1),
            ) { items.itemCount }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { page -> items.peekOrNull(page)?.id ?: (-page - 1L) },
            ) { page ->
                ViewerPage(
                    item = items.getOrNull(page),
                    isCurrent = pagerState.settledPage == page,
                    chromeVisible = chromeVisible,
                    onChromeChange = { chromeVisible = it },
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
                    ActionBar(
                        actions = when {
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
                        },
                    )
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
) {
    when {
        item == null -> Box(Modifier.fillMaxSize())
        item.isVideo -> VideoPage(item, isCurrent, onChromeChange)
        else -> PhotoPage(item, isCurrent, onTap = { onChromeChange(!chromeVisible) })
    }
}

@Composable
private fun PhotoPage(item: MediaItem, isCurrent: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    // Layer 1: the system thumbnail appears instantly. Layer 2: the sharper image fades in on top.
    val thumbnail = remember(item.uri) {
        ImageRequest.Builder(context).data(item.uri).size(ThumbnailRequestSize).build()
    }
    val full = remember(item.uri) {
        ImageRequest.Builder(context).data(item.uri).size(FullRequestSize).crossfade(true).build()
    }
    ZoomableBox(isCurrent = isCurrent, onTap = onTap) { onImageSize ->
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
private fun VideoPage(item: MediaItem, isCurrent: Boolean, onChromeChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val thumbnail = remember(item.uri) {
        ImageRequest.Builder(context).data(item.uri).size(ThumbnailRequestSize).build()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        // Only the settled page plays, so swiping past a video releases its player.
        if (isCurrent) {
            VideoPlayer(
                uri = item.uri,
                onControllerVisibilityChange = onChromeChange,
                // Keep the player's own seek bar clear of our bottom action bar.
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 72.dp),
            )
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
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
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
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
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
    DisposableEffect(enabled) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
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

private fun LazyPagingItems<MediaItem>.peekOrNull(index: Int): MediaItem? =
    if (index in 0 until itemCount) peek(index) else null

private fun LazyPagingItems<MediaItem>.getOrNull(index: Int): MediaItem? =
    if (index in 0 until itemCount) get(index) else null
