package com.femi.gallery.presentation.pictures

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.femi.gallery.domain.model.toRef
import com.femi.gallery.presentation.common.HideBottomBarWhile
import com.femi.gallery.presentation.selection.SelectionBars
import com.femi.gallery.presentation.selection.libraryActions
import com.femi.gallery.presentation.selection.WriteRequestHandler

@Composable
fun PicturesScreen(
    onMediaClick: (mediaIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PicturesViewModel = hiltViewModel(),
) {
    val items = viewModel.media.collectAsLazyPagingItems()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    val selectionState by viewModel.selection.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    HideBottomBarWhile(selectionState.active)
    BackHandler(enabled = selectionState.active) { viewModel.selection.clear() }
    WriteRequestHandler(batches = viewModel.selection.batches, onSuccess = viewModel.selection::clear)

    Box(modifier = modifier.fillMaxSize()) {
        TimelineGrid(
            items = items,
            sections = sections,
            columns = columns,
            onMediaClick = { mediaIndex, item ->
                if (selectionState.active) viewModel.selection.toggle(item.toRef()) else onMediaClick(mediaIndex)
            },
            onMediaLongClick = { item -> viewModel.selection.begin(item.toRef()) },
            onZoomIn = viewModel::zoomIn,
            onZoomOut = viewModel::zoomOut,
            topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            selectionActive = selectionState.active,
            selectedIds = selectionState.ids,
        )

        if (selectionState.active) {
            SelectionBars(
                count = selectionState.count,
                actions = libraryActions(context, selectionState, viewModel.selection),
                onClose = viewModel.selection::clear,
                onSelectAll = viewModel.selection::selectAll,
            )
        }
    }
}
