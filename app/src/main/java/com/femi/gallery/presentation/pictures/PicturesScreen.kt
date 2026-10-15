package com.femi.gallery.presentation.pictures

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage

/**
 * Minimal grid used to verify the data layer. The real timeline (date headers, pinch-to-zoom
 * columns, fast scroller) replaces this in the next patch.
 */
/** Temporary: prints each item's id and file name on its cell to compare with `adb` output. */
private const val SHOW_DEBUG_LABELS = true

@Composable
fun PicturesScreen(
    modifier: Modifier = Modifier,
    viewModel: PicturesViewModel = hiltViewModel(),
) {
    val items = viewModel.media.collectAsLazyPagingItems()

    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    val gridState = rememberLazyGridState()

    // When a newer photo arrives, jump to it if the user is already near the top.
    val newestId = items.peek(0)?.id
    LaunchedEffect(newestId) {
        if (newestId != null && gridState.firstVisibleItemIndex < 24) gridState.scrollToItem(0)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(1.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        items(
            count = items.itemCount,
            key = { index -> items.peek(index)?.id ?: -index.toLong() - 1 },
        ) { index ->
            val item = items[index]
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (item != null) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (SHOW_DEBUG_LABELS) {
                        Text(
                            text = "${item.id}\n${item.displayName.takeLast(14)}",
                            color = Color.White,
                            fontSize = 8.sp,
                            lineHeight = 9.sp,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(2.dp),
                        )
                    }
                    if (item.isVideo) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}
