@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)
@file:Suppress("OPT_IN_USAGE_FUTURE_ERROR")

package com.femi.gallery.presentation.stories

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.LayoutInflater
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.femi.gallery.playback.PlayerFactory
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.femi.gallery.R
import com.femi.gallery.domain.model.SlideshowMusicTrack
import com.femi.gallery.domain.model.SlideshowOrder
import com.femi.gallery.domain.model.SlideshowSpeed
import com.femi.gallery.presentation.common.EmptyState
import com.femi.gallery.presentation.common.ImmersiveMode
import kotlinx.coroutines.delay
import kotlin.random.Random
import androidx.media3.common.MediaItem as PlayerMediaItem

/**
 * Plays photos one after another (a story, a place or an album). Tap to pause or resume, swipe to
 * skip, X to close. While paused, speed, shuffle, and music can be changed.
 *
 * In list order the photos are read from the paged list as the show moves along. A shuffled show is
 * different: the whole list is loaded and shuffled first (with a loading indicator), then played from
 * memory, because random positions in a paged list are mostly not loaded yet and would show black slides.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SlideshowScreen(
    onClose: () -> Unit,
    viewModel: SlideshowViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val items = viewModel.media.collectAsLazyPagingItems()
    val shuffledUris by viewModel.shuffled.collectAsStateWithLifecycle()
    val speed by viewModel.slideshowSpeed.collectAsStateWithLifecycle()
    val shuffle by viewModel.slideshowShuffle.collectAsStateWithLifecycle()
    val musicEnabled by viewModel.slideshowMusicEnabled.collectAsStateWithLifecycle()
    val musicTrack by viewModel.slideshowMusicTrack.collectAsStateWithLifecycle()
    val customMusicUri by viewModel.slideshowCustomMusicUri.collectAsStateWithLifecycle()
    val customMusicName by viewModel.slideshowCustomMusicName.collectAsStateWithLifecycle()

    var playing by remember { mutableStateOf(true) }
    var seed by rememberSaveable { mutableLongStateOf(Random.nextLong()) }
    var showMusicDialog by remember { mutableStateOf(false) }
    var isCurrentVideo by remember { mutableStateOf(false) }

    ImmersiveMode(enabled = true)

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
            }
            val name = queryAudioDisplayName(context, uri) ?: "Custom song"
            viewModel.setSlideshowCustomMusic(uri.toString(), name)
        }
    }

    // Background music player
    val musicPlayer = remember(context) {
        PlayerFactory.create(context).apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    DisposableEffect(musicPlayer) {
        onDispose {
            musicPlayer.release()
        }
    }

    // Set or switch media item for background music
    LaunchedEffect(musicPlayer, musicTrack, customMusicUri) {
        val mediaUri = if (musicTrack == SlideshowMusicTrack.Custom && !customMusicUri.isNullOrEmpty()) {
            try {
                Uri.parse(customMusicUri)
            } catch (_: Exception) {
                Uri.parse("android.resource://${context.packageName}/${R.raw.slideshow_ambient}")
            }
        } else {
            Uri.parse("android.resource://${context.packageName}/${R.raw.slideshow_ambient}")
        }
        musicPlayer.setMediaItem(PlayerMediaItem.fromUri(mediaUri))
        musicPlayer.prepare()
    }

    // Fall back to default theme if custom audio fails
    DisposableEffect(musicPlayer, musicTrack) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (musicTrack == SlideshowMusicTrack.Custom) {
                    viewModel.setSlideshowMusicTrack(SlideshowMusicTrack.Default)
                }
            }
        }
        musicPlayer.addListener(listener)
        onDispose { musicPlayer.removeListener(listener) }
    }

    // Start or stop music playback in sync with slideshow playing state and music toggle
    val isLoaded = items.loadState.refresh is LoadState.NotLoading
    val hasItems = (shuffle && (shuffledUris?.isNotEmpty() == true)) || (!shuffle && items.itemCount > 0)
    val shouldPlayMusic = musicEnabled && playing && hasItems

    LaunchedEffect(shouldPlayMusic) {
        if (shouldPlayMusic) {
            musicPlayer.play()
        } else {
            musicPlayer.pause()
        }
    }

    // Duck the background music volume when a video slide is playing
    val targetVolume = if (isCurrentVideo && playing) 0.15f else 1.0f
    val animatedVolume by animateFloatAsState(
        targetValue = targetVolume,
        animationSpec = tween(durationMillis = 350),
        label = "slideshowMusicVolume",
    )
    LaunchedEffect(animatedVolume) {
        musicPlayer.volume = animatedVolume
    }

    // Turning shuffle on prepares a new random order; turning it off goes back to the list order.
    LaunchedEffect(shuffle, seed) {
        if (shuffle) viewModel.shuffle(seed) else viewModel.stopShuffle()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                playing = !playing
            },
    ) {
        val preparing = shuffle && shuffledUris == null
        when {
            preparing -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)

            shuffle -> {
                val shuffledItems = shuffledUris.orEmpty()
                if (shuffledItems.isEmpty()) {
                    EmptyState(text = stringResource(R.string.slideshow_empty), textColor = Color.White.copy(alpha = 0.8f))
                } else {
                    SlidePager(
                        pageCount = shuffledItems.size,
                        playing = playing,
                        speed = speed,
                        itemAt = { shuffledItems.getOrNull(it) },
                        onCurrentIsVideoChanged = { isCurrentVideo = it },
                    )
                }
            }

            isLoaded && items.itemCount == 0 ->
                EmptyState(text = stringResource(R.string.slideshow_empty), textColor = Color.White.copy(alpha = 0.8f))

            items.itemCount > 0 ->
                SlidePager(
                    pageCount = items.itemCount,
                    playing = playing,
                    speed = speed,
                    itemAt = { items[it]?.let { item -> SlideshowItem(item.uri, item.isVideo) } },
                    onCurrentIsVideoChanged = { isCurrentVideo = it },
                )
        }

        if (!playing && !preparing) {
            Icon(
                imageVector = Icons.Filled.Pause,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp),
            )
        }

        if (!playing && items.itemCount > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = shuffle,
                    onClick = {
                        val nextShuffle = !shuffle
                        viewModel.setSlideshowShuffle(nextShuffle)
                        if (nextShuffle) seed = Random.nextLong()
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.slideshow_shuffle),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    shape = RoundedCornerShape(28.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Black.copy(alpha = 0.75f),
                        labelColor = Color.White,
                        iconColor = Color.White,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = shuffle,
                        borderColor = Color.White.copy(alpha = 0.25f),
                        selectedBorderColor = Color.Transparent,
                    ),
                )

                AssistChip(
                    onClick = { viewModel.setSlideshowSpeed(speed.next()) },
                    label = {
                        Text(
                            text = stringResource(
                                when (speed) {
                                    SlideshowSpeed.Slow -> R.string.slideshow_speed_slow
                                    SlideshowSpeed.Normal -> R.string.slideshow_speed_normal
                                    SlideshowSpeed.Fast -> R.string.slideshow_speed_fast
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    shape = RoundedCornerShape(28.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color.Black.copy(alpha = 0.75f),
                        labelColor = Color.White,
                        leadingIconContentColor = Color.White,
                    ),
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = Color.White.copy(alpha = 0.25f),
                    ),
                )

                FilterChip(
                    selected = musicEnabled,
                    onClick = { showMusicDialog = true },
                    label = {
                        val musicLabel = when {
                            !musicEnabled -> stringResource(R.string.slideshow_music_off)
                            musicTrack == SlideshowMusicTrack.Custom && !customMusicName.isNullOrBlank() -> customMusicName.orEmpty()
                            else -> stringResource(R.string.slideshow_music_theme)
                        }
                        Text(
                            text = musicLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (musicEnabled) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    shape = RoundedCornerShape(28.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Black.copy(alpha = 0.75f),
                        labelColor = Color.White,
                        iconColor = Color.White,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = musicEnabled,
                        borderColor = Color.White.copy(alpha = 0.25f),
                        selectedBorderColor = Color.Transparent,
                    ),
                )
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 8.dp, start = 8.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.slideshow_close), tint = Color.White)
        }
    }

    if (showMusicDialog) {
        AlertDialog(
            onDismissRequest = { showMusicDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.slideshow_music_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Option 1: Theme music (bundled default)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setSlideshowMusicEnabled(true)
                                viewModel.setSlideshowMusicTrack(SlideshowMusicTrack.Default)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = musicEnabled && musicTrack == SlideshowMusicTrack.Default,
                            onClick = {
                                viewModel.setSlideshowMusicEnabled(true)
                                viewModel.setSlideshowMusicTrack(SlideshowMusicTrack.Default)
                            },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.slideshow_music_theme),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    // Option 2: Custom song
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!customMusicUri.isNullOrEmpty()) {
                                    viewModel.setSlideshowMusicEnabled(true)
                                    viewModel.setSlideshowMusicTrack(SlideshowMusicTrack.Custom)
                                } else {
                                    audioPickerLauncher.launch(arrayOf("audio/*"))
                                }
                            }
                            .padding(vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = musicEnabled && musicTrack == SlideshowMusicTrack.Custom,
                                onClick = {
                                    if (!customMusicUri.isNullOrEmpty()) {
                                        viewModel.setSlideshowMusicEnabled(true)
                                        viewModel.setSlideshowMusicTrack(SlideshowMusicTrack.Custom)
                                    } else {
                                        audioPickerLauncher.launch(arrayOf("audio/*"))
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (!customMusicName.isNullOrBlank()) {
                                    customMusicName.orEmpty()
                                } else {
                                    stringResource(R.string.slideshow_music_custom)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        TextButton(
                            onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                            modifier = Modifier.padding(start = 40.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    if (customMusicUri.isNullOrEmpty()) R.string.slideshow_music_choose
                                    else R.string.slideshow_music_change
                                ),
                            )
                        }
                    }

                    // Option 3: Music Off
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setSlideshowMusicEnabled(false)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = !musicEnabled,
                            onClick = {
                                viewModel.setSlideshowMusicEnabled(false)
                            },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.slideshow_music_off),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMusicDialog = false }) {
                    Text(stringResource(R.string.slideshow_music_done))
                }
            },
        )
    }
}

private fun queryAudioDisplayName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) return cursor.getString(index)
                }
            }
        } catch (_: Exception) {
        }
    }
    return uri.lastPathSegment
}

/**
 * [pageCount] items (photos and videos), swiped or advanced on a timer. [itemAt] gives the item of a page, or null
 * while it is not available. It starts again from the first item after the last.
 */
@OptIn(UnstableApi::class)
@Composable
private fun SlidePager(
    pageCount: Int,
    playing: Boolean,
    speed: SlideshowSpeed,
    itemAt: (Int) -> SlideshowItem?,
    onCurrentIsVideoChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState { pageCount }
    val progress = remember { Animatable(0f) }
    var videoEnded by remember { mutableStateOf(false) }

    val settledItem = itemAt(pagerState.settledPage)
    val currentIsVideo = settledItem?.isVideo == true

    LaunchedEffect(currentIsVideo) {
        onCurrentIsVideoChanged(currentIsVideo)
    }

    LaunchedEffect(pagerState.settledPage, settledItem?.uri) {
        videoEnded = false
    }

    // Advance after each slide has been shown for a while (or video ends), with animated progress indicator.
    LaunchedEffect(playing, pagerState.settledPage, pageCount, speed, settledItem?.isVideo, videoEnded) {
        if (!playing || pageCount <= 1) {
            progress.stop()
            return@LaunchedEffect
        }

        if (settledItem?.isVideo == true) {
            if (videoEnded) {
                val next = SlideshowOrder.pageAfter(pagerState.settledPage, pageCount)
                if (next > pagerState.settledPage) pagerState.animateScrollToPage(next) else pagerState.scrollToPage(next)
            }
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = speed.millis.toInt(),
                    easing = LinearEasing,
                ),
            )
            val next = SlideshowOrder.pageAfter(pagerState.settledPage, pageCount)
            if (next > pagerState.settledPage) pagerState.animateScrollToPage(next) else pagerState.scrollToPage(next)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            val item = itemAt(page)
            if (item != null) {
                if (item.isVideo) {
                    val isCurrent = pagerState.settledPage == page
                    SlideshowVideoPlayer(
                        uri = item.uri,
                        isCurrent = isCurrent,
                        playing = playing && isCurrent,
                        onProgress = { pos, dur ->
                            if (isCurrent && dur > 0) {
                                progress.snapTo((pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f))
                            }
                        },
                        onEnded = {
                            if (isCurrent) videoEnded = true
                        },
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(item.uri).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (pageCount > 1) {
            LinearWavyProgressIndicator(
                progress = { progress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp)
                    .align(Alignment.TopCenter),
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SlideshowVideoPlayer(
    uri: String,
    isCurrent: Boolean,
    playing: Boolean,
    onProgress: suspend (pos: Long, dur: Long) -> Unit,
    onEnded: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(uri) {
        PlayerFactory.create(context).apply {
            setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ false)
            setMediaItem(PlayerMediaItem.fromUri(uri))
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    LaunchedEffect(playing, isCurrent) {
        if (isCurrent && playing) {
            player.play()
        } else {
            player.pause()
        }
    }

    DisposableEffect(player, isCurrent) {
        if (!isCurrent) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    onEnded()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                onEnded()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, playing, isCurrent) {
        if (!isCurrent) return@LaunchedEffect
        while (true) {
            val dur = player.duration.coerceAtLeast(0L)
            val pos = player.currentPosition.coerceAtLeast(0L)
            onProgress(pos, dur)
            if (!playing) break
            delay(100L)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
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
        onRelease = { it.player = null },
    )
}
