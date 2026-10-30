package com.femi.gallery.presentation.pictures

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.LazyPagingItems
import com.femi.gallery.R
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.TimelineSection
import com.femi.gallery.domain.model.toRef
import com.femi.gallery.presentation.common.EmptyState
import com.femi.gallery.presentation.common.HideBottomBarWhile
import com.femi.gallery.presentation.common.HugeTopAppBar
import com.femi.gallery.presentation.common.ViewedMedia
import com.femi.gallery.presentation.selection.SelectionBars
import com.femi.gallery.presentation.selection.SelectionHost
import com.femi.gallery.presentation.selection.libraryActions
import com.femi.gallery.presentation.selection.libraryMoreActions
import com.femi.gallery.presentation.selection.selectMediaRange
import com.femi.gallery.presentation.selection.toggleSectionSelection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PicturesScreen(
    modifier: Modifier = Modifier,
    onMediaClick: (mediaIndex: Int, mediaId: Long) -> Unit,
    viewed: ViewedMedia? = null,
    onViewedConsumed: () -> Unit = {},
    viewModel: PicturesViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(
        checkNotNull<androidx.lifecycle.ViewModelStoreOwner>(LocalViewModelStoreOwner.current) {
                "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
            },
        null
    ),
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
                        title = stringResource(R.string.nav_pictures),
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
                    if (selectionState.active) viewModel.selection.toggle(item.toRef()) else onMediaClick(mediaIndex, item.id)
                },
                onMediaLongClick = { item -> viewModel.selection.begin(item.toRef()) },
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
                    selectMediaRange(start, end, items, viewModel.selection)
                },
                viewed = viewed,
                onViewedConsumed = onViewedConsumed,
                emptyContent = { EmptyState(stringResource(R.string.pictures_empty)) },
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
