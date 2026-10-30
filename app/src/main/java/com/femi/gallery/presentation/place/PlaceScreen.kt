package com.femi.gallery.presentation.place

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.femi.gallery.R
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.femi.gallery.domain.model.MediaRef
import com.femi.gallery.domain.model.toRef
import com.femi.gallery.presentation.common.EmptyState
import com.femi.gallery.presentation.common.HideBottomBarWhile
import com.femi.gallery.presentation.common.HugeTopAppBar
import com.femi.gallery.presentation.common.ViewedMedia
import com.femi.gallery.presentation.pictures.TimelineGrid
import com.femi.gallery.presentation.selection.SelectionBottomBar
import com.femi.gallery.presentation.selection.SelectionHost
import com.femi.gallery.presentation.selection.SelectionTopBar
import com.femi.gallery.presentation.selection.libraryActions
import com.femi.gallery.presentation.selection.libraryMoreActions
import com.femi.gallery.presentation.selection.selectMediaRange
import com.femi.gallery.presentation.selection.toggleSectionSelection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onMediaClick: (mediaIndex: Int, mediaId: Long, query: String) -> Unit,
    onPlaySlideshow: (query: String) -> Unit,
    viewed: ViewedMedia? = null,
    onViewedConsumed: () -> Unit = {},
    viewModel: PlaceViewModel = hiltViewModel(),
) {
    val items = viewModel.media.collectAsLazyPagingItems()
    val sections by viewModel.timeline.sections.collectAsStateWithLifecycle()
    val zoom by viewModel.timeline.zoom.collectAsStateWithLifecycle()
    val selectionState by viewModel.selection.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var dragBaseSelection by remember { mutableStateOf<Map<Long, MediaRef>>(emptyMap()) }

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
                Box(modifier = Modifier.fillMaxWidth()) {
                    HugeTopAppBar(
                        title = stringResource(R.string.place_title),
                        onBack = onBack,
                        actions = {
                            IconButton(onClick = { onPlaySlideshow(viewModel.query) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.story_slideshow))
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        modifier = if (selectionState.active) Modifier.graphicsLayer { alpha = 0f } else Modifier,
                    )
                    if (selectionState.active) {
                        SelectionTopBar(
                            count = selectionState.count,
                            onClose = viewModel.selection::clear,
                            onSelectAll = viewModel.selection::selectAll,
                        )
                    }
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
                onMediaLongClick = { item ->
                    dragBaseSelection = selectionState.selected
                    viewModel.selection.begin(item.toRef())
                },
                onZoomIn = viewModel.timeline::zoomIn,
                onZoomOut = viewModel.timeline::zoomOut,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
                selectionActive = selectionState.active,
                selectedIds = selectionState.ids,
                onHeaderClick = { section ->
                    toggleSectionSelection(section, items, selectionState.ids, viewModel.selection)
                },
                onDragSelectRange = { start, end ->
                    selectMediaRange(start, end, items, viewModel.selection, dragBaseSelection)
                },
                viewed = viewed,
                onViewedConsumed = onViewedConsumed,
                emptyContent = { EmptyState(stringResource(R.string.place_empty)) },
            )
        }

        SelectionBottomBar(
            active = selectionState.active,
            count = selectionState.count,
            actions = libraryActions(context, selectionState, viewModel.selection),
            moreActions = libraryMoreActions(context, viewModel.selection),
        )
    }
}
