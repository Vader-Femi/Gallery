package com.femi.gallery.presentation.albums

import android.net.Uri
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
import com.femi.gallery.presentation.common.shareUris
import com.femi.gallery.presentation.pictures.TimelineGrid
import com.femi.gallery.presentation.selection.SelectionBars
import com.femi.gallery.presentation.selection.WriteRequestHandler

@Composable
fun AlbumScreen(
    onBack: () -> Unit,
    onMediaClick: (bucketId: Long, mediaIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val items = viewModel.media.collectAsLazyPagingItems()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
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
                    text = title.ifEmpty { stringResource(R.string.album_unnamed) },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TimelineGrid(
                items = items,
                sections = sections,
                columns = columns,
                onMediaClick = { mediaIndex, item ->
                    if (selectionState.active) {
                        viewModel.selection.toggle(item.toRef())
                    } else {
                        onMediaClick(viewModel.bucketId, mediaIndex)
                    }
                },
                onMediaLongClick = { item -> viewModel.selection.begin(item.toRef()) },
                onZoomIn = viewModel::zoomIn,
                onZoomOut = viewModel::zoomOut,
                modifier = Modifier.weight(1f),
                selectionActive = selectionState.active,
                selectedIds = selectionState.ids,
            )
        }

        if (selectionState.active) {
            SelectionBars(
                count = selectionState.count,
                allFavorite = selectionState.selected.values.let { refs -> refs.isNotEmpty() && refs.all { it.isFavorite } },
                onClose = viewModel.selection::clear,
                onSelectAll = viewModel.selection::selectAll,
                onShare = { shareUris(context, selectionState.selected.values.map { Uri.parse(it.uri) }) },
                onFavorite = viewModel.selection::toggleFavoriteSelected,
                onDelete = viewModel.selection::trashSelected,
            )
        }
    }
}
