@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)
@file:Suppress("OPT_IN_USAGE_FUTURE_ERROR")

package com.femi.gallery.presentation.stories

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.femi.gallery.R
import com.femi.gallery.domain.model.SlideshowOrder
import com.femi.gallery.domain.model.SlideshowSpeed
import com.femi.gallery.presentation.common.EmptyState
import com.femi.gallery.presentation.common.ImmersiveMode
import com.femi.gallery.presentation.settings.ThemePreferences
import kotlinx.coroutines.delay
import kotlin.random.Random
import androidx.media3.common.MediaItem as PlayerMediaItem

/**
 * Plays photos one after another (a story, a place or an album). Tap to pause or resume, swipe to
 * skip, X to close. While paused, the speed and shuffle can be changed.
 *
 * In list order the photos are read from the paged list as the show moves along. A shuffled show is
 * different: the whole list is loaded and shuffled first (with a loading indicator), then played from
 * memory, because random positions in a paged list are mostly not loaded yet and would show black slides.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun SlideshowScreen(
    onClose: () -> Unit,
    viewModel: SlideshowViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val items = viewModel.media.collectAsLazyPagingItems()
    val shuffledUris by viewModel.shuffled.collectAsStateWithLifecycle()
    var playing by remember { mutableStateOf(true) }
    var speed by rememberSaveable { mutableStateOf(ThemePreferences.getSlideshowSpeed(context)) }
    var shuffle by rememberSaveable { mutableStateOf(ThemePreferences.getSlideshowShuffle(context)) }
    var seed by rememberSaveable { mutableLongStateOf(Random.nextLong()) }

    ImmersiveMode(enabled = true)

    LaunchedEffect(speed) {
        ThemePreferences.setSlideshowSpeed(context, speed)
    }

    LaunchedEffect(shuffle) {
        ThemePreferences.setSlideshowShuffle(context, shuffle)
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
        val isLoaded = items.loadState.refresh is LoadState.NotLoading
        val preparing = shuffle && shuffledUris == null
        when {
            preparing -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)

            shuffle -> {
                val shuffledItems = shuffledUris.orEmpty()
                if (shuffledItems.isEmpty()) {
                    EmptyState(text = stringResource(R.string.slideshow_empty), textColor = Color.White.copy(alpha = 0.8f))
                } else {
                    SlidePager(pageCount = shuffledItems.size, playing = playing, speed = speed, itemAt = { shuffledItems.getOrNull(it) })
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
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilterChip(
                    selected = shuffle,
                    onClick = {
                        shuffle = !shuffle
                        if (shuffle) seed = Random.nextLong()
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
                    onClick = { speed = speed.next() },
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
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState { pageCount }
    val progress = remember { Animatable(0f) }
    var videoEnded by remember { mutableStateOf(false) }

    val settledItem = itemAt(pagerState.settledPage)

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
                    easing = LinearEasing
                )
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
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
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
        onRelease = { it.player = null }
    )
}
