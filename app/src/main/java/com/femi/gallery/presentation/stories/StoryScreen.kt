package com.femi.gallery.presentation.stories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.femi.gallery.R
import com.femi.gallery.domain.model.toRef
import com.femi.gallery.presentation.common.EmptyState
import com.femi.gallery.presentation.common.HideBottomBarWhile
import com.femi.gallery.presentation.common.HugeTopAppBar
import com.femi.gallery.presentation.common.ViewedMedia
import com.femi.gallery.presentation.pictures.TimelineGrid
import com.femi.gallery.presentation.selection.SelectionBars
import com.femi.gallery.presentation.selection.SelectionHost
import com.femi.gallery.presentation.selection.libraryActions
import com.femi.gallery.presentation.selection.libraryMoreActions

@OptIn(ExperimentalMaterial3Api::class)
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    HideBottomBarWhile(selectionState.active)
    BackHandler(enabled = selectionState.active) { viewModel.selection.clear() }
    SelectionHost(controller = viewModel.selection, onSuccess = viewModel.selection::clear)

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                if (!selectionState.active) {
                    HugeTopAppBar(
                        title = StoryTitles.range(viewModel.fromDay, viewModel.toDay),
                        onBack = onBack,
                        actions = {
                            IconButton(onClick = { onPlaySlideshow(viewModel.query) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.story_slideshow))
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                }
            },
        ) { innerPadding ->
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
                selectionActive = selectionState.active,
                selectedIds = selectionState.ids,
                viewed = viewed,
                onViewedConsumed = onViewedConsumed,
                emptyContent = { EmptyState(stringResource(R.string.story_empty)) },
            )
        }

        SelectionBars(
            active = selectionState.active,
            count = selectionState.count,
            actions = libraryActions(context, selectionState, viewModel.selection),
            moreActions = libraryMoreActions(context, viewModel.selection),
            onClose = viewModel.selection::clear,
            onSelectAll = viewModel.selection::selectAll,
        )
    }
}
