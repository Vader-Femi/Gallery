package com.femi.gallery.presentation.albums

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.femi.gallery.presentation.locked.LockedGate
import com.femi.gallery.presentation.pictures.TimelineGrid
import com.femi.gallery.presentation.selection.SelectionBars
import com.femi.gallery.presentation.selection.SelectionHost
import com.femi.gallery.presentation.selection.libraryActions
import com.femi.gallery.presentation.selection.libraryMoreActions
import com.femi.gallery.presentation.selection.lockedActions
import com.femi.gallery.presentation.selection.recycleBinActions

@Composable
fun AlbumScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onMediaClick: (bucketId: Long, mediaIndex: Int, mediaId: Long) -> Unit,
    onPlaySlideshow: (bucketId: Long) -> Unit = {},
    viewed: ViewedMedia? = null,
    onViewedConsumed: () -> Unit = {},
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    if (viewModel.isLocked) {
        // Nothing from the Locked folder is shown until the user authenticates.
        LockedGate(session = viewModel.lockSession, onClose = onBack) {
            AlbumContent(onBack, onMediaClick, onPlaySlideshow, viewed, onViewedConsumed, viewModel, modifier)
        }
    } else {
        AlbumContent(onBack, onMediaClick, onPlaySlideshow, viewed, onViewedConsumed, viewModel, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumContent(
    onBack: () -> Unit,
    onMediaClick: (bucketId: Long, mediaIndex: Int, mediaId: Long) -> Unit,
    onPlaySlideshow: (bucketId: Long) -> Unit,
    viewed: ViewedMedia?,
    onViewedConsumed: () -> Unit,
    viewModel: AlbumViewModel,
    modifier: Modifier,
) {
    val items = viewModel.media.collectAsLazyPagingItems()
    val sections by viewModel.timeline.sections.collectAsStateWithLifecycle()
    val zoom by viewModel.timeline.zoom.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
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

    val albumTitle = when {
        viewModel.isRecycleBin -> stringResource(R.string.album_trash)
        viewModel.isFavorites -> stringResource(R.string.album_favorites)
        viewModel.isLocked -> stringResource(R.string.album_locked)
        else -> title.ifEmpty { stringResource(R.string.album_unnamed) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                if (!selectionState.active) {
                    HugeTopAppBar(
                        title = albumTitle,
                        onBack = onBack,
                        actions = {
                            // Not for the recycle bin, and not for the Locked folder (nothing from it is shown outside its own screen).
                            if (!viewModel.isRecycleBin && !viewModel.isLocked && items.itemCount > 0) {
                                IconButton(onClick = { onPlaySlideshow(viewModel.bucketId) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.story_slideshow))
                                }
                            }
                            if (viewModel.isRecycleBin && items.itemCount > 0) {
                                TextButton(onClick = viewModel.selection::emptyBin) {
                                    Text(stringResource(R.string.action_empty_bin))
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                }
            },
        ) { innerPadding ->
            val emptyText = when {
                viewModel.isRecycleBin -> stringResource(R.string.recycle_bin_empty)
                viewModel.isFavorites -> stringResource(R.string.favorites_empty)
                viewModel.isLocked -> stringResource(R.string.locked_empty)
                else -> stringResource(R.string.album_empty)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
            ) {
                if (viewModel.isRecycleBin) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.recycle_bin_info),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                            onMediaClick(viewModel.bucketId, mediaIndex, item.id)
                        }
                    },
                    onMediaLongClick = { item -> viewModel.selection.begin(item.toRef()) },
                    onZoomIn = viewModel.timeline::zoomIn,
                    onZoomOut = viewModel.timeline::zoomOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    selectionActive = selectionState.active,
                    selectedIds = selectionState.ids,
                    viewed = viewed,
                    onViewedConsumed = onViewedConsumed,
                    emptyContent = { EmptyState(emptyText) },
                )
            }
        }

        SelectionBars(
            active = selectionState.active,
            count = selectionState.count,
            actions = when {
                viewModel.isRecycleBin -> recycleBinActions(viewModel.selection)
                viewModel.isLocked -> lockedActions(viewModel.selection)
                else -> libraryActions(context, selectionState, viewModel.selection)
            },
            moreActions = if (viewModel.isRecycleBin || viewModel.isLocked) {
                emptyList()
            } else {
                libraryMoreActions(context, viewModel.selection)
            },
            onClose = viewModel.selection::clear,
            onSelectAll = viewModel.selection::selectAll,
        )
    }
}
