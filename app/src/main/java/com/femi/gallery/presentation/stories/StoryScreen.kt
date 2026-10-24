package com.femi.gallery.presentation.stories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.femi.gallery.R
import com.femi.gallery.domain.model.toRef
import com.femi.gallery.presentation.common.HideBottomBarWhile
import com.femi.gallery.presentation.common.ViewedMedia
import com.femi.gallery.presentation.pictures.TimelineGrid
import com.femi.gallery.presentation.selection.SelectionBars
import com.femi.gallery.presentation.selection.SelectionHost
import com.femi.gallery.presentation.selection.libraryActions
import com.femi.gallery.presentation.selection.libraryMoreActions

@Composable
fun StoryScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onMediaClick: (mediaIndex: Int, mediaId: Long, query: String) -> Unit,
    onPlaySlideshow: (query: String) -> Unit,
    viewed: ViewedMedia? = null,
    onViewedConsumed: () -> Unit = {},
    viewModel: StoryViewModel = hiltViewModel(),
) {
    val items = viewModel.media.collectAsLazyPagingItems()
    val sections by viewModel.timeline.sections.collectAsStateWithLifecycle()
    val zoom by viewModel.timeline.zoom.collectAsStateWithLifecycle()
    val selectionState by viewModel.selection.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    HideBottomBarWhile(selectionState.active)
    BackHandler(enabled = selectionState.active) { viewModel.selection.clear() }
    SelectionHost(controller = viewModel.selection, onSuccess = viewModel.selection::clear)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.viewer_back))
                }
                Text(
                    text = StoryTitles.range(viewModel.fromDay, viewModel.toDay),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onPlaySlideshow(viewModel.query) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.story_slideshow))
                }
            }
            TimelineGrid(
                items = items,
                sections = sections,
                zoom = zoom,
                onMediaClick = { mediaIndex, item ->
                    if (selectionState.active) {
                        viewModel.selection.toggle(item.toRef())
                    } else {
                        onMediaClick(mediaIndex, item.id, viewModel.query)
                    }
                },
                onMediaLongClick = { item -> viewModel.selection.begin(item.toRef()) },
                onZoomIn = viewModel.timeline::zoomIn,
                onZoomOut = viewModel.timeline::zoomOut,
                modifier = Modifier.weight(1f),
                selectionActive = selectionState.active,
                selectedIds = selectionState.ids,
                viewed = viewed,
                onViewedConsumed = onViewedConsumed,
            )
        }

        if (selectionState.active) {
            SelectionBars(
                count = selectionState.count,
                actions = libraryActions(context, selectionState, viewModel.selection),
                moreActions = libraryMoreActions(context, viewModel.selection),
                onClose = viewModel.selection::clear,
                onSelectAll = viewModel.selection::selectAll,
            )
        }
    }
}
