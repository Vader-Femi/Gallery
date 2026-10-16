package com.femi.gallery.presentation.selection

import android.content.Context
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.femi.gallery.R
import com.femi.gallery.presentation.common.shareUris

/** Share, favorite and move-to-trash, for photos in the library or a normal album. */
@Composable
fun libraryActions(
    context: Context,
    state: SelectionManager.State,
    controller: MediaSelectionController,
): List<SelectionAction> {
    val allFavorite = state.selected.values.let { refs -> refs.isNotEmpty() && refs.all { it.isFavorite } }
    return listOf(
        SelectionAction(Icons.Outlined.Share, stringResource(R.string.action_share)) {
            shareUris(context, state.selected.values.map { Uri.parse(it.uri) })
        },
        SelectionAction(
            icon = if (allFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            label = stringResource(if (allFavorite) R.string.action_unfavorite else R.string.action_favorite),
            onClick = controller::toggleFavoriteSelected,
        ),
        SelectionAction(Icons.Outlined.Delete, stringResource(R.string.action_delete), controller::trashSelected),
    )
}

/** Restore and delete-forever, for items in the recycle bin. */
@Composable
fun recycleBinActions(controller: MediaSelectionController): List<SelectionAction> = listOf(
    SelectionAction(Icons.Outlined.RestoreFromTrash, stringResource(R.string.action_restore), controller::restoreSelected),
    SelectionAction(Icons.Outlined.DeleteForever, stringResource(R.string.action_delete_forever), controller::deleteForeverSelected),
)
