package com.femi.gallery.presentation.pictures

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems

@Composable
fun PicturesScreen(
    onMediaClick: (mediaIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PicturesViewModel = hiltViewModel(),
) {
    val items = viewModel.media.collectAsLazyPagingItems()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    TimelineGrid(
        items = items,
        sections = sections,
        columns = columns,
        onMediaClick = onMediaClick,
        onZoomIn = viewModel::zoomIn,
        onZoomOut = viewModel::zoomOut,
        modifier = modifier,
        topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
    )
}
