package com.femi.gallery.presentation.picker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.femi.gallery.R
import com.femi.gallery.domain.model.PickerFilter
import com.femi.gallery.domain.model.toRef
import com.femi.gallery.presentation.common.EmptyState
import com.femi.gallery.presentation.pictures.TimelineGrid

/**
 * Shown when another app asks for a photo or video ("Choose from Gallery"). Tapping a photo
 * returns it right away; when several are allowed, tick them and press Done.
 */
@Composable
fun PickerScreen(
    filter: PickerFilter,
    allowMultiple: Boolean,
    onPick: (uris: List<String>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PickerViewModel = hiltViewModel(),
) {
    LaunchedEffect(filter) { viewModel.setFilter(filter) }

    val items = viewModel.media.collectAsLazyPagingItems()
    val sections by viewModel.timeline.sections.collectAsStateWithLifecycle()
    val zoom by viewModel.timeline.zoom.collectAsStateWithLifecycle()
    val selectionState by viewModel.selection.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.picker_cancel_selection))
            }
            Text(
                text = when {
                    allowMultiple && selectionState.count > 0 -> stringResource(R.string.selection_count, selectionState.count)
                    filter == PickerFilter.ImagesOnly -> stringResource(R.string.picker_title_photo)
                    filter == PickerFilter.VideosOnly -> stringResource(R.string.picker_title_video)
                    else -> stringResource(R.string.picker_title_any)
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (allowMultiple) {
                Button(
                    onClick = { onPick(selectionState.selected.values.map { it.uri }) },
                    enabled = selectionState.count > 0,
                    modifier = Modifier.padding(end = 8.dp),
                ) { Text(stringResource(R.string.picker_done)) }
            }
        }

        TimelineGrid(
            items = items,
            sections = sections,
            zoom = zoom,
            onMediaClick = { _, item ->
                if (allowMultiple) viewModel.selection.toggle(item.toRef()) else onPick(listOf(item.uri))
            },
            onMediaLongClick = { item -> if (allowMultiple) viewModel.selection.toggle(item.toRef()) },
            onZoomIn = viewModel.timeline::zoomIn,
            onZoomOut = viewModel.timeline::zoomOut,
            modifier = Modifier.weight(1f),
            selectionActive = allowMultiple,
            selectedIds = selectionState.ids,
            emptyContent = { EmptyState(stringResource(R.string.picker_empty)) },
        )
    }
}
