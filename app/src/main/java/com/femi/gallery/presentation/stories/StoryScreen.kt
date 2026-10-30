package com.femi.gallery.presentation.stories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.femi.gallery.R
import com.femi.gallery.domain.model.StoryFilter
import com.femi.gallery.domain.model.StoryTitle
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
import com.femi.gallery.presentation.selection.selectMediaRange
import com.femi.gallery.presentation.selection.toggleSectionSelection

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
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val hasVideos by viewModel.hasVideos.collectAsStateWithLifecycle()
    val customTitle by viewModel.customTitle.collectAsStateWithLifecycle()
    val hasCustomCover by viewModel.hasCustomCover.collectAsStateWithLifecycle()
    var pickingCover by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val dateTitle = StoryTitles.range(viewModel.fromDay, viewModel.toDay)
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    HideBottomBarWhile(selectionState.active)
    BackHandler(enabled = selectionState.active) { viewModel.selection.clear() }
    BackHandler(enabled = pickingCover) { pickingCover = false }
    SelectionHost(controller = viewModel.selection, onSuccess = viewModel.selection::clear)

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                if (!selectionState.active) {
                    HugeTopAppBar(
                        title = if (pickingCover) stringResource(R.string.story_pick_cover) else customTitle ?: dateTitle,
                        onBack = { if (pickingCover) pickingCover = false else onBack() },
                        actions = {
                            if (!pickingCover) {
                                IconButton(onClick = { onPlaySlideshow(viewModel.slideshowQuery) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.story_slideshow))
                                }
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.rename_album_confirm)) },
                                        onClick = {
                                            menuOpen = false
                                            renaming = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.story_change_cover)) },
                                        onClick = {
                                            menuOpen = false
                                            pickingCover = true
                                        },
                                    )
                                    if (hasCustomCover) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.story_reset_cover)) },
                                            onClick = {
                                                menuOpen = false
                                                viewModel.resetCover()
                                            },
                                        )
                                    }
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
            ) {
                if (hasVideos && !pickingCover) {
                    StoryFilterChips(selected = filter, onSelect = viewModel::setFilter)
                }
                TimelineGrid(
                    items = items,
                    sections = sections,
                    zoom = zoom,
                    onMediaClick = { mediaIndex, item ->
                        if (pickingCover) {
                            viewModel.setCover(item.id)
                            pickingCover = false
                        } else if (selectionState.active) {
                            viewModel.selection.toggle(item.toRef())
                        } else {
                            onMediaClick(mediaIndex, item.id, viewModel.query)
                        }
                    },
                    onMediaLongClick = { item -> if (!pickingCover) viewModel.selection.begin(item.toRef()) },
                    onZoomIn = viewModel.timeline::zoomIn,
                    onZoomOut = viewModel.timeline::zoomOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
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
                    emptyContent = { EmptyState(stringResource(R.string.story_empty)) },
                )
            }
        }

        if (renaming) {
            RenameStoryDialog(
                initial = customTitle ?: dateTitle,
                hasCustomTitle = customTitle != null,
                onSave = { text ->
                    renaming = false
                    // Typing the date title back, or nothing, goes back to the dates.
                    viewModel.rename(text.takeUnless { StoryTitle.clean(it) == dateTitle })
                },
                onReset = {
                    renaming = false
                    viewModel.rename(null)
                },
                onDismiss = { renaming = false },
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

@Composable
private fun StoryFilterChips(selected: StoryFilter, onSelect: (StoryFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StoryFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        stringResource(
                            when (filter) {
                                StoryFilter.All -> R.string.story_filter_all
                                StoryFilter.Photos -> R.string.nav_pictures
                                StoryFilter.Videos -> R.string.menu_videos
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun RenameStoryDialog(
    initial: String,
    hasCustomTitle: Boolean,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.story_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(StoryTitle.MAX_LENGTH) },
                singleLine = true,
                label = { Text(stringResource(R.string.story_title_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.rename_album_confirm)) }
        },
        dismissButton = {
            Row {
                if (hasCustomTitle) {
                    TextButton(onClick = onReset) { Text(stringResource(R.string.story_use_dates)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_cancel)) }
            }
        },
    )
}
