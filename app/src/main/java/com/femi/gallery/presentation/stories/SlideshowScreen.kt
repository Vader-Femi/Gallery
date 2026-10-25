package com.femi.gallery.presentation.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.femi.gallery.R
import com.femi.gallery.presentation.common.EmptyState
import com.femi.gallery.presentation.common.ImmersiveMode
import kotlinx.coroutines.delay

private const val SlideDurationMs = 3_500L

/** Plays a story's photos one after another. Tap to pause or resume, swipe to skip, X to close. */
@Composable
fun SlideshowScreen(
    onClose: () -> Unit,
    viewModel: SlideshowViewModel = hiltViewModel(),
) {
    val items = viewModel.media.collectAsLazyPagingItems()
    val context = LocalContext.current
    var playing by remember { mutableStateOf(true) }

    ImmersiveMode(enabled = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                playing = !playing
            },
    ) {
        val isLoaded = items.loadState.refresh is androidx.paging.LoadState.NotLoading
        if (isLoaded && items.itemCount == 0) {
            EmptyState(
                text = stringResource(R.string.slideshow_empty),
                textColor = Color.White.copy(alpha = 0.8f),
            )
        } else if (items.itemCount > 0) {
            val pagerState = rememberPagerState { items.itemCount }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                val item = items[page]
                if (item != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(item.uri).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Advance after each slide has been shown for a while; restarts whenever the page changes.
            LaunchedEffect(playing, pagerState.settledPage, items.itemCount) {
                if (playing && items.itemCount > 1) {
                    delay(SlideDurationMs)
                    val next = pagerState.settledPage + 1
                    if (next < items.itemCount) pagerState.animateScrollToPage(next) else pagerState.scrollToPage(0)
                }
            }
        }

        if (!playing) {
            Icon(
                imageVector = Icons.Filled.Pause,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp),
            )
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
