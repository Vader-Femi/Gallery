package com.femi.gallery.presentation.albums

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.femi.gallery.R
import com.femi.gallery.domain.model.Album
import com.femi.gallery.domain.model.AlbumPaths
import com.femi.gallery.domain.model.SpecialAlbums
import com.femi.gallery.presentation.common.HugeTopAppBar
import com.femi.gallery.presentation.common.pinchToChangeColumns
import com.femi.gallery.presentation.selection.NameDialog
import com.femi.gallery.presentation.selection.SelectionHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onAlbumClick: (bucketId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    SelectionHost(controller = viewModel.actions, onSuccess = {})
    var renaming by remember { mutableStateOf<Album?>(null) }
    renaming?.let { album ->
        NameDialog(
            title = stringResource(R.string.rename_album_title),
            confirmLabel = stringResource(R.string.rename_album_confirm),
            initialName = album.name,
            onConfirm = { name ->
                viewModel.actions.renameAlbum(album, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    val handleAlbumClick by rememberUpdatedState(onAlbumClick)
    var columns by rememberSaveable { mutableIntStateOf(2) }
    val haptic = LocalHapticFeedback.current

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            HugeTopAppBar(
                title = stringResource(R.string.nav_albums),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .pinchToChangeColumns(
                    onZoomIn = {
                        if (columns > 2) {
                            columns--
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onZoomOut = {
                        if (columns < 5) {
                            columns++
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                ),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(albums, key = { it.bucketId }) { album ->
                val handleClick: () -> Unit = remember(album.bucketId) { { handleAlbumClick(album.bucketId) } }
                val handleRename: () -> Unit = remember(album.bucketId) { { renaming = album } }
                AlbumCard(
                    album = album,
                    onClick = handleClick,
                    onRename = handleRename,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit, onRename: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    // Only real folders under DCIM, Pictures or Movies can be renamed (not the camera folder or virtual albums).
    val canRename = album.bucketId !in setOf(SpecialAlbums.FAVORITES, SpecialAlbums.TRASH, SpecialAlbums.LOCKED) &&
        AlbumPaths.canRename(album.relativePath)

    Column(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { if (canRename) menuOpen = true },
        ),
    ) {
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename_album)) },
                onClick = {
                    menuOpen = false
                    onRename()
                },
            )
        }
        if (album.bucketId == SpecialAlbums.LOCKED) {
            // Nothing from the Locked folder is shown outside it, so it gets a lock instead of a cover.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            AsyncImage(
                model = album.coverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Text(
            text = when (album.bucketId) {
                SpecialAlbums.FAVORITES -> stringResource(R.string.album_favorites)
                SpecialAlbums.TRASH -> stringResource(R.string.album_trash)
                SpecialAlbums.LOCKED -> stringResource(R.string.album_locked)
                else -> album.name.ifEmpty { stringResource(R.string.album_unnamed) }
            },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = pluralStringResource(R.plurals.album_item_count, album.itemCount, album.itemCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
