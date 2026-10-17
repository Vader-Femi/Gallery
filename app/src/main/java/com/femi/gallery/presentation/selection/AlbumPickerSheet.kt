package com.femi.gallery.presentation.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.femi.gallery.R
import com.femi.gallery.domain.model.AlbumPaths

/** Bottom sheet for choosing the album to move or copy to, with a "New album" option. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPickerSheet(
    mode: PickerMode,
    onChoose: (relativePath: String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: AlbumPickerViewModel = hiltViewModel(),
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    val handleChoose by androidx.compose.runtime.rememberUpdatedState(onChoose)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(if (mode == PickerMode.Move) R.string.picker_move_title else R.string.picker_copy_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.navigationBarsPadding()) {
            item(key = "new") {
                PickerRow(
                    onClick = { creating = true },
                    leading = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    title = stringResource(R.string.picker_new_album),
                    subtitle = null,
                )
            }
            items(albums, key = { it.bucketId }) { album ->
                val handleRowClick: () -> Unit = remember(album.bucketId) { { handleChoose(album.relativePath) } }
                PickerRow(
                    onClick = handleRowClick,
                    leading = {
                        AsyncImage(
                            model = album.coverUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp),
                        )
                    },
                    title = album.name.ifEmpty { stringResource(R.string.album_unnamed) },
                    subtitle = pluralStringResource(R.plurals.album_item_count, album.itemCount, album.itemCount),
                )
            }
        }
    }

    if (creating) {
        NameDialog(
            title = stringResource(R.string.picker_new_album),
            confirmLabel = stringResource(R.string.picker_create),
            initialName = "",
            onConfirm = { name -> onChoose(AlbumPaths.newAlbumPath(name)) },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun PickerRow(
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { leading() }
        androidx.compose.foundation.layout.Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Asks for an album name; the confirm button stays disabled until the name is usable. */
@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    onConfirm: (cleanName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialName) }
    val cleanName = AlbumPaths.sanitizeName(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.picker_album_name)) },
            )
        },
        confirmButton = {
            TextButton(enabled = cleanName != null, onClick = { cleanName?.let(onConfirm) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_cancel)) } },
    )
}
