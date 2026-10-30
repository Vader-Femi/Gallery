package com.femi.gallery.presentation.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.femi.gallery.R
import com.femi.gallery.domain.model.toRef
import com.femi.gallery.presentation.common.HideBottomBarWhile
import com.femi.gallery.presentation.common.ViewedMedia
import com.femi.gallery.presentation.pictures.TimelineGrid
import com.femi.gallery.presentation.selection.SelectionBars
import com.femi.gallery.presentation.selection.SelectionHost
import com.femi.gallery.presentation.selection.libraryActions
import com.femi.gallery.presentation.selection.libraryMoreActions
import com.femi.gallery.presentation.selection.selectMediaRange
import com.femi.gallery.presentation.selection.toggleSectionSelection
import com.femi.gallery.domain.text.TextScanState
import com.femi.gallery.domain.text.TextScanStatus
import java.text.NumberFormat

/** Suggestion chips: the label shown, and the (English) query it fills in. */
private val Suggestions = listOf(
    R.string.search_suggestion_videos to "videos",
    R.string.search_suggestion_favorites to "favorites",
    R.string.search_suggestion_screenshots to "screenshots",
    R.string.search_suggestion_yesterday to "yesterday",
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    initialQuery: String = "",
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onMediaClick: (mediaIndex: Int, mediaId: Long, query: String) -> Unit,
    viewed: ViewedMedia? = null,
    onViewedConsumed: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotEmpty() && query.isEmpty()) {
            viewModel.onQueryChange(initialQuery)
        }
    }
    val criteria by viewModel.criteria.collectAsStateWithLifecycle()
    val textScan by viewModel.textScan.collectAsStateWithLifecycle()
    val items = viewModel.media.collectAsLazyPagingItems()
    val sections by viewModel.timeline.sections.collectAsStateWithLifecycle()
    val zoom by viewModel.timeline.zoom.collectAsStateWithLifecycle()
    val selectionState by viewModel.selection.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    HideBottomBarWhile(selectionState.active)
    BackHandler(enabled = selectionState.active) { viewModel.selection.clear() }
    SelectionHost(controller = viewModel.selection, onSuccess = viewModel.selection::clear)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            val handleClear: () -> Unit = remember { { viewModel.onQueryChange("") } }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.viewer_back))
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = handleClear) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )

            TextScanNotice(textScan)

            Box(modifier = Modifier.weight(1f)) {
                when {
                    criteria.isEmpty -> SearchHelp(onSuggestion = viewModel::onQueryChange)

                    items.itemCount == 0 && items.loadState.refresh is LoadState.Loading -> {
                        SearchLoadingIndicator()
                    }

                    items.itemCount == 0 && items.loadState.refresh !is LoadState.Loading -> {
                        Text(
                            text = stringResource(R.string.search_no_results),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                        )
                    }

                    // A new search starts with a fresh grid (scroll position, zoom anchor).
                    else -> key(criteria) {
                        val active by rememberUpdatedState(selectionState.active)
                        val trimmedQuery by remember { derivedStateOf { query.trim() } }
                        val handleMediaClick: (Int, com.femi.gallery.domain.model.MediaItem) -> Unit = remember {
                            { mediaIndex, item ->
                                if (active) {
                                    viewModel.selection.toggle(item.toRef())
                                } else {
                                    onMediaClick(mediaIndex, item.id, trimmedQuery)
                                }
                            }
                        }
                        val handleMediaLongClick: (com.femi.gallery.domain.model.MediaItem) -> Unit = remember {
                            { item -> viewModel.selection.begin(item.toRef()) }
                        }

                        TimelineGrid(
                            items = items,
                            sections = sections,
                            zoom = zoom,
                            onMediaClick = handleMediaClick,
                            onMediaLongClick = handleMediaLongClick,
                            onZoomIn = viewModel.timeline::zoomIn,
                            onZoomOut = viewModel.timeline::zoomOut,
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
                        )
                    }
                }
            }
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SearchHelp(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.search_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Suggestions.forEach { (label, text) ->
                SuggestionChip(onClick = { onSuggestion(text) }, label = { Text(stringResource(label)) })
            }
        }
    }
}

/**
 * While the text in the photos is still being read, say so: a search only finds words in the photos
 * that have been read, so fewer results than expected are not a mistake. Nothing is shown otherwise.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TextScanNotice(state: TextScanState) {
    when (state.status) {
        TextScanStatus.Scanning -> Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
            val numbers = NumberFormat.getIntegerInstance()
            Text(
                text = stringResource(R.string.search_reading_text, numbers.format(state.scanned), numbers.format(state.total)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearWavyProgressIndicator(
                progress = { if (state.total == 0) 0f else state.scanned.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        TextScanStatus.Unavailable -> Text(
            text = stringResource(R.string.search_text_unavailable),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        TextScanStatus.Idle -> Unit
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchLoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(
            modifier = Modifier.size(64.dp).offset(y = (-48).dp),
        )
    }
}
