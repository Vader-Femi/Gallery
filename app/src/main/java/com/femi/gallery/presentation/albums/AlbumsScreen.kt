package com.femi.gallery.presentation.albums

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import com.femi.gallery.presentation.common.rememberFoldablePostureInfo
import com.femi.gallery.presentation.common.rememberWindowWidthClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalDensity
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
import com.femi.gallery.presentation.common.EmptyState
import com.femi.gallery.presentation.common.HugeTopAppBar
import com.femi.gallery.presentation.common.ViewedMedia
import com.femi.gallery.presentation.common.pinchToChangeColumns
import com.femi.gallery.presentation.selection.NameDialog
import com.femi.gallery.presentation.selection.SelectionHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onAlbumClick: (bucketId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onMediaClick: (bucketId: Long, mediaIndex: Int, mediaId: Long) -> Unit = { _, _, _ -> },
    onPlaySlideshow: (bucketId: Long) -> Unit = {},
    viewed: ViewedMedia? = null,
    onViewedConsumed: () -> Unit = {},
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val windowWidthClass = rememberWindowWidthClass()
    val foldableInfo = rememberFoldablePostureInfo()
    val hasVerticalHinge = foldableInfo.verticalHinge != null
    val isTwoPane = windowWidthClass.isExpanded || (windowWidthClass.isMedium && hasVerticalHinge)

    var selectedBucketId by rememberSaveable { mutableStateOf<Long?>(null) }
    val activeBucketId = selectedBucketId ?: albums.firstOrNull()?.bucketId

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
    val defaultColumns = when {
        isTwoPane -> 2
        windowWidthClass.isExpanded -> 4
        windowWidthClass.isMedium -> 3
        else -> 2
    }
    var columns by rememberSaveable(defaultColumns) { mutableIntStateOf(defaultColumns) }
    val haptic = LocalHapticFeedback.current

    if (isTwoPane) {
        val density = LocalDensity.current

        // Target fold X coordinate in window coordinates:
        // Only set when a physical vertical hinge exists (foldables). For tablets, this is null
        // so the left pane stays at a compact 250.dp sidebar, allocating more space to the images on the right.
        val targetFoldWindowXPx: Float? = foldableInfo.verticalHinge?.left

        var localFoldXPx by remember(targetFoldWindowXPx) {
            val initial = if (targetFoldWindowXPx != null) {
                // Initial estimate on frame 1: subtract start padding if nav rail is shown
                val startPaddingPx = with(density) { if (windowWidthClass.isMediumOrExpanded) 104.dp.toPx() else 0f }
                (targetFoldWindowXPx - startPaddingPx).coerceAtLeast(0f)
            } else null
            mutableStateOf(initial)
        }

        val leftPaneWidth: androidx.compose.ui.unit.Dp = localFoldXPx?.let {
            if (it > 0f) with(density) { it.toDp() } else null
        } ?: 250.dp

        val vHinge = foldableInfo.verticalHinge
        val hingeGapDp: androidx.compose.ui.unit.Dp = if (vHinge != null && vHinge.width > 0f) {
            with(density) { vHinge.width.toDp() }
        } else {
            0.dp
        }

        Row(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    if (targetFoldWindowXPx != null && coordinates.isAttached) {
                        val localOffset = coordinates.windowToLocal(Offset(targetFoldWindowXPx, 0f))
                        val containerWidth = coordinates.size.width.toFloat()
                        if (localOffset.x in 120f..(containerWidth - 120f)) {
                            localFoldXPx = localOffset.x
                        }
                    }
                },
        ) {
            // Left Pane: Albums List
            Surface(
                modifier = Modifier
                    .width(leftPaneWidth)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        HugeTopAppBar(
                            title = stringResource(R.string.nav_albums),
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { innerPadding ->
                    if (albums.isEmpty()) {
                        EmptyState(
                            text = stringResource(R.string.albums_empty),
                            modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
                        )
                    } else if (leftPaneWidth < 320.dp) {
                        // Compact sidebar layout for tablets: sleek list rows with thumbnail, title and count
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = innerPadding.calculateTopPadding()),
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            lazyColumnItems(albums, key = { it.bucketId }, contentType = { "two_pane_album_row" }) { album ->
                                val handleClick: () -> Unit = {
                                    if (album.bucketId == SpecialAlbums.PEOPLE) {
                                        handleAlbumClick(album.bucketId)
                                    } else {
                                        selectedBucketId = album.bucketId
                                    }
                                }
                                val handleRename: () -> Unit = { renaming = album }
                                TwoPaneAlbumRow(
                                    album = album,
                                    isSelected = album.bucketId == activeBucketId,
                                    onClick = handleClick,
                                    onRename = handleRename,
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = innerPadding.calculateTopPadding()),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(albums, key = { it.bucketId }, contentType = { "album" }) { album ->
                                val handleClick: () -> Unit = {
                                    if (album.bucketId == SpecialAlbums.PEOPLE) {
                                        handleAlbumClick(album.bucketId)
                                    } else {
                                        selectedBucketId = album.bucketId
                                    }
                                }
                                val handleRename: () -> Unit = { renaming = album }
                                AlbumCard(
                                    album = album,
                                    isSelected = album.bucketId == activeBucketId,
                                    onClick = handleClick,
                                    onRename = handleRename,
                                )
                            }
                        }
                    }
                }
            }

            if (hingeGapDp > 1.dp) {
                Box(
                    modifier = Modifier
                        .width(hingeGapDp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            } else {
                androidx.compose.material3.VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            // Right Pane: Selected Album Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                if (activeBucketId != null) {
                    key(activeBucketId) {
                        AlbumScreen(
                            bucketId = activeBucketId,
                            onBack = { selectedBucketId = null },
                            onMediaClick = onMediaClick,
                            onPlaySlideshow = onPlaySlideshow,
                            viewed = viewed,
                            onViewedConsumed = onViewedConsumed,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    EmptyState(
                        text = stringResource(R.string.albums_empty),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    } else {
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
            if (albums.isEmpty()) {
                EmptyState(
                    text = stringResource(R.string.albums_empty),
                    modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
                )
            } else {
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
                                if (columns < 6) {
                                    columns++
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                        ),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(albums, key = { it.bucketId }, contentType = { "album" }) { album ->
                        val handleClick: () -> Unit = remember(album.bucketId) { { handleAlbumClick(album.bucketId) } }
                        val handleRename: () -> Unit = remember(album.bucketId) { { renaming = album } }
                        AlbumCard(
                            album = album,
                            isSelected = false,
                            onClick = handleClick,
                            onRename = handleRename,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TwoPaneAlbumRow(
    album: Album,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val canRename = album.bucketId !in setOf(SpecialAlbums.FAVORITES, SpecialAlbums.TRASH, SpecialAlbums.LOCKED) &&
        AlbumPaths.canRename(album.relativePath)

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (canRename) menuOpen = true },
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
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
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                AsyncImage(
                    model = album.coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (album.bucketId) {
                        SpecialAlbums.FAVORITES -> stringResource(R.string.album_favorites)
                        SpecialAlbums.TRASH -> stringResource(R.string.album_trash)
                        SpecialAlbums.LOCKED -> stringResource(R.string.album_locked)
                        SpecialAlbums.PEOPLE -> stringResource(R.string.album_people)
                        else -> album.name.ifEmpty { stringResource(R.string.album_unnamed) }
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.album_item_count, album.itemCount, album.itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    album: Album,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onRename: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Only real folders under DCIM, Pictures or Movies can be renamed (not the camera folder or virtual albums).
    val canRename = album.bucketId !in setOf(SpecialAlbums.FAVORITES, SpecialAlbums.TRASH, SpecialAlbums.LOCKED) &&
        AlbumPaths.canRename(album.relativePath)

    val shape = RoundedCornerShape(20.dp)
    val selectionModifier = if (isSelected) {
        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
    } else {
        Modifier
    }

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
                    .then(selectionModifier)
                    .clip(shape)
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
                    .then(selectionModifier)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Text(
            text = when (album.bucketId) {
                SpecialAlbums.FAVORITES -> stringResource(R.string.album_favorites)
                SpecialAlbums.TRASH -> stringResource(R.string.album_trash)
                SpecialAlbums.LOCKED -> stringResource(R.string.album_locked)
                SpecialAlbums.PEOPLE -> stringResource(R.string.album_people)
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
