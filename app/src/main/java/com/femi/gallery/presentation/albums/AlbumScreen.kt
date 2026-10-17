package com.femi.gallery.presentation.albums

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
import androidx.compose.material3.TextButton
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
import com.femi.gallery.presentation.locked.LockedGate
import com.femi.gallery.presentation.pictures.TimelineGrid
import com.femi.gallery.presentation.selection.SelectionBars
import com.femi.gallery.presentation.selection.libraryActions
import com.femi.gallery.presentation.selection.lockedActions
import com.femi.gallery.presentation.selection.recycleBinActions
import com.femi.gallery.presentation.selection.WriteRequestHandler

@Composable
fun AlbumScreen(
    onBack: () -> Unit,
    onMediaClick: (bucketId: Long, mediaIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    if (viewModel.isLocked) {
        // Nothing from the Locked folder is shown until the user authenticates.
        LockedGate(session = viewModel.lockSession, onClose = onBack) {
            AlbumContent(onBack, onMediaClick, viewModel, modifier)
        }
    } else {
        AlbumContent(onBack, onMediaClick, viewModel, modifier)
    }
}

@Composable
private fun AlbumContent(
    onBack: () -> Unit,
    onMediaClick: (bucketId: Long, mediaIndex: Int) -> Unit,
    viewModel: AlbumViewModel,
    modifier: Modifier,
) {
    val items = viewModel.media.collectAsLazyPagingItems()
    val sections by viewModel.timeline.sections.collectAsStateWithLifecycle()
    val zoom by viewModel.timeline.zoom.collectAsStateWithLifecycle()
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
                    text = when {
                        viewModel.isRecycleBin -> stringResource(R.string.album_trash)
                        viewModel.isFavorites -> stringResource(R.string.album_favorites)
                        viewModel.isLocked -> stringResource(R.string.album_locked)
                        else -> title.ifEmpty { stringResource(R.string.album_unnamed) }
                    },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (viewModel.isRecycleBin && items.itemCount > 0) {
                    TextButton(onClick = viewModel.selection::emptyBin) {
                        Text(stringResource(R.string.action_empty_bin))
                    }
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
                        onMediaClick(viewModel.bucketId, mediaIndex)
                    }
                },
                onMediaLongClick = { item -> viewModel.selection.begin(item.toRef()) },
                onZoomIn = viewModel.timeline::zoomIn,
                onZoomOut = viewModel.timeline::zoomOut,
                modifier = Modifier.weight(1f),
                selectionActive = selectionState.active,
                selectedIds = selectionState.ids,
            )
        }

        if (selectionState.active) {
            SelectionBars(
                count = selectionState.count,
                actions = when {
                    viewModel.isRecycleBin -> recycleBinActions(viewModel.selection)
                    viewModel.isLocked -> lockedActions(viewModel.selection)
                    else -> libraryActions(context, selectionState, viewModel.selection)
                },
                onClose = viewModel.selection::clear,
                onSelectAll = viewModel.selection::selectAll,
            )
        }
    }
}
