package com.femi.gallery.presentation.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Plays photos one after another (a story, a place or an album). Tap to pause or resume, swipe to
 * skip, X to close. While paused, the speed and shuffle can be changed.
 *
 * In list order the photos are read from the paged list as the show moves along. A shuffled show is
 * different: the whole list is loaded and shuffled first (with a loading indicator), then played from
 * memory, because random positions in a paged list are mostly not loaded yet and would show black slides.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SlideshowScreen(
    onClose: () -> Unit,
    viewModel: SlideshowViewModel = hiltViewModel(),
) {
    val items = viewModel.media.collectAsLazyPagingItems()
    val shuffledUris by viewModel.shuffled.collectAsStateWithLifecycle()
    var playing by remember { mutableStateOf(true) }
    var speed by rememberSaveable { mutableStateOf(SlideshowSpeed.Default) }
    var shuffle by rememberSaveable { mutableStateOf(false) }
    var seed by rememberSaveable { mutableLongStateOf(Random.nextLong()) }

    ImmersiveMode(enabled = true)

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
            preparing -> LoadingIndicator(modifier = Modifier.align(Alignment.Center))

            shuffle -> {
                val uris = shuffledUris.orEmpty()
                if (uris.isEmpty()) {
                    EmptyState(text = stringResource(R.string.slideshow_empty), textColor = Color.White.copy(alpha = 0.8f))
                } else {
                    SlidePager(pageCount = uris.size, playing = playing, speed = speed, uriAt = { uris.getOrNull(it) })
                }
            }

            isLoaded && items.itemCount == 0 ->
                EmptyState(text = stringResource(R.string.slideshow_empty), textColor = Color.White.copy(alpha = 0.8f))

            items.itemCount > 0 ->
                SlidePager(pageCount = items.itemCount, playing = playing, speed = speed, uriAt = { items[it]?.uri })
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
                    label = { Text(stringResource(R.string.slideshow_shuffle)) },
                    leadingIcon = { Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = { speed = speed.next() },
                    label = {
                        Text(
                            stringResource(
                                when (speed) {
                                    SlideshowSpeed.Slow -> R.string.slideshow_speed_slow
                                    SlideshowSpeed.Normal -> R.string.slideshow_speed_normal
                                    SlideshowSpeed.Fast -> R.string.slideshow_speed_fast
                                },
                            ),
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(4.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.slideshow_close), tint = Color.White)
        }
    }
}

/**
 * [pageCount] pictures, swiped or advanced on a timer. [uriAt] gives the picture of a page, or null
 * while it is not available. It starts again from the first picture after the last.
 */
@Composable
private fun SlidePager(
    pageCount: Int,
    playing: Boolean,
    speed: SlideshowSpeed,
    uriAt: (Int) -> String?,
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState { pageCount }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
    ) { page ->
        val uri = uriAt(page)
        if (uri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(uri).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // Advance after each slide has been shown for a while; restarts whenever the page or the speed changes.
    LaunchedEffect(playing, pagerState.settledPage, pageCount, speed) {
        if (playing && pageCount > 1) {
            delay(speed.millis)
            val next = SlideshowOrder.pageAfter(pagerState.settledPage, pageCount)
            if (next > pagerState.settledPage) pagerState.animateScrollToPage(next) else pagerState.scrollToPage(next)
        }
    }
}
