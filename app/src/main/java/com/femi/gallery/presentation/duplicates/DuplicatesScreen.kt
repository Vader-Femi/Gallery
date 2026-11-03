package com.femi.gallery.presentation.duplicates

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.femi.gallery.R
import com.femi.gallery.domain.duplicates.DuplicateGroup
import com.femi.gallery.domain.duplicates.DuplicatePhase
import com.femi.gallery.domain.duplicates.DuplicateProgress
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.presentation.common.EmptyState
import com.femi.gallery.presentation.common.HugeTopAppBar
import com.femi.gallery.presentation.selection.WriteRequestHandler

private val ThumbnailSize = 104.dp
private const val ThumbnailPixels = 256

/** The words one use of the groups screen shows. */
private class GroupsTexts(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    @StringRes val hint: Int,
    @StringRes val none: Int,
    @StringRes val remove: Int,
    /** Similar photos: a group can be left alone, and a group is not "copies". */
    val similar: Boolean,
)

private val DuplicatesTexts = GroupsTexts(
    title = R.string.duplicates_title,
    summary = R.string.duplicates_summary,
    hint = R.string.duplicates_hint,
    none = R.string.duplicates_none,
    remove = R.string.duplicates_remove,
    similar = false,
)

private val SimilarTexts = GroupsTexts(
    title = R.string.similar_title,
    summary = R.string.similar_summary,
    hint = R.string.similar_hint,
    none = R.string.similar_none,
    remove = R.string.similar_remove,
    similar = true,
)

/**
 * Identical photos and videos, grouped. In each group one file is marked to keep (a good guess is
 * made, and tapping another file changes it); the button at the bottom moves all the other copies to
 * the recycle bin, where they stay for a while and can be restored.
 */
@Composable
fun DuplicatesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DuplicatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WriteRequestHandler(batches = viewModel.selection.batches, onSuccess = viewModel::onTrashed)
    GroupsScreen(
        texts = DuplicatesTexts,
        state = state,
        onBack = onBack,
        onRemove = viewModel::removeCopies,
        onKeep = viewModel::keep,
        onIgnore = null,
        modifier = modifier,
    )
}

/**
 * Photos that look alike (resized or re-saved copies, the shots of a burst), found across the whole library. Same
 * layout as the duplicates, but each group can be left alone.
 */
@Composable
fun SimilarPhotosScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SimilarPhotosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WriteRequestHandler(batches = viewModel.selection.batches, onSuccess = viewModel::onTrashed)
    GroupsScreen(
        texts = SimilarTexts,
        state = state,
        onBack = onBack,
        onRemove = viewModel::removeExtras,
        onKeep = viewModel::keep,
        onIgnore = viewModel::ignore,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GroupsScreen(
    texts: GroupsTexts,
    state: DuplicatesUiState,
    onBack: () -> Unit,
    onRemove: () -> Unit,
    onKeep: (groupIndex: Int, mediaId: Long) -> Unit,
    onIgnore: ((groupIndex: Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HugeTopAppBar(
                title = stringResource(texts.title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (!state.scanning && state.copiesToRemove > 0) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                    Button(
                        onClick = onRemove,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(stringResource(texts.remove, state.copiesToRemove))
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.scanning -> Scanning(state.progress)
                state.groups.isEmpty() -> EmptyState(text = stringResource(texts.none))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(
                                    texts.summary,
                                    state.groups.size,
                                    Formatter.formatShortFileSize(context, state.bytesToFree),
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stringResource(texts.hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    itemsIndexed(state.groups, key = { _, group -> group.items.first().id }) { index, group ->
                        GroupCard(
                            group = group,
                            similar = texts.similar,
                            onKeep = { onKeep(index, it) },
                            onIgnore = onIgnore?.let { ignore -> { ignore(index) } },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Scanning(progress: DuplicateProgress?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (progress == null) {
            LoadingIndicator()
        } else {
            Text(
                text = stringResource(
                    when (progress.phase) {
                        DuplicatePhase.Sampling -> R.string.duplicates_sampling
                        DuplicatePhase.Verifying -> R.string.duplicates_verifying
                        DuplicatePhase.Hashing -> R.string.similar_hashing
                    },
                    progress.done,
                    progress.total,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearWavyProgressIndicator(
                progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GroupCard(group: DuplicateGroup, similar: Boolean, onKeep: (Long) -> Unit, onIgnore: (() -> Unit)?) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (similar) {
                        stringResource(R.string.similar_group, group.items.size)
                    } else {
                        stringResource(
                            R.string.duplicates_copies,
                            group.items.size,
                            Formatter.formatShortFileSize(context, group.items.first().sizeBytes),
                        )
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (onIgnore != null) {
                    TextButton(onClick = onIgnore) { Text(stringResource(R.string.similar_keep_all)) }
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(group.items, key = { it.id }) { item ->
                    Copy(item = item, kept = item.id == group.keepId, onClick = { onKeep(item.id) })
                }
            }
        }
    }
}

@Composable
private fun Copy(item: MediaItem, kept: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val request = remember(item.uri) { ImageRequest.Builder(context).data(item.uri).size(ThumbnailPixels).build() }
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.size(width = ThumbnailSize, height = ThumbnailSize + 36.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(ThumbnailSize)
                .clip(shape)
                .then(if (kept) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                .clickable(onClick = onClick),
        ) {
            AsyncImage(
                model = request,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    // The copies that will be removed are dimmed, the one that stays is not.
                    .alpha(if (kept) 1f else 0.55f),
            )
            if (kept) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp),
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(R.string.duplicates_keep),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(3.dp),
                    )
                }
            }
            if (item.isVideo) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(20.dp),
                )
            }
        }
        // Where this copy lives tells the copies apart: the same photo in Camera and in WhatsApp.
        Text(
            text = item.bucketName.ifEmpty { item.relativePath },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
