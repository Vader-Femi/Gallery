package com.femi.gallery.presentation.selection

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import com.femi.gallery.R
import com.femi.gallery.presentation.common.shareUris
import com.femi.gallery.presentation.locked.BiometricAuth

/** Share, favorite and move-to-trash, for photos in the library or a normal album. */
@Composable
fun libraryActions(
    context: Context,
    state: SelectionManager.State,
    controller: MediaSelectionController,
): List<SelectionAction> {
    val allFavorite = state.selected.values.let { refs -> refs.isNotEmpty() && refs.all { it.isFavorite } }
    val latestSelected by rememberUpdatedState(state.selected)

    val shareLabel = stringResource(R.string.action_share)
    val favoriteLabel = stringResource(R.string.action_favorite)
    val unfavoriteLabel = stringResource(R.string.action_unfavorite)
    val deleteLabel = stringResource(R.string.action_delete)

    return remember(allFavorite, context, controller) {
        listOf(
            SelectionAction(Icons.Outlined.Share, shareLabel) {
                shareUris(context, latestSelected.values.map { Uri.parse(it.uri) })
            },
            SelectionAction(
                icon = if (allFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = if (allFavorite) unfavoriteLabel else favoriteLabel,
                onClick = controller::toggleFavoriteSelected,
            ),
            SelectionAction(Icons.Outlined.Delete, deleteLabel, controller::trashSelected),
        )
    }
}

/** Move, copy and lock: the less common actions behind the bar's "More" button. */
@Composable
fun libraryMoreActions(
    context: Context,
    controller: MediaSelectionController,
): List<SelectionAction> {
    val moveToAlbumLabel = stringResource(R.string.action_move_to_album)
    val copyToAlbumLabel = stringResource(R.string.action_copy_to_album)
    val lockLabel = stringResource(R.string.action_lock)
    
    return remember(context, controller) {
        listOf(
            SelectionAction(Icons.Outlined.DriveFileMove, moveToAlbumLabel, controller::moveSelectedToAlbum),
            SelectionAction(Icons.Outlined.ContentCopy, copyToAlbumLabel, controller::copySelectedToAlbum),
            SelectionAction(Icons.Outlined.Lock, lockLabel) {
                lockWithScreenLockCheck(context) { controller.lockSelected() }
            },
        )
    }
}

/** Moving to the Locked folder only makes sense if the phone has a screen lock to protect it. */
fun lockWithScreenLockCheck(context: Context, lock: () -> Unit) {
    if (BiometricAuth.isAvailable(context)) {
        lock()
    } else {
        Toast.makeText(context, R.string.locked_needs_screen_lock, Toast.LENGTH_LONG).show()
    }
}

/** Restore and delete-forever, for items in the recycle bin. */
@Composable
fun recycleBinActions(controller: MediaSelectionController): List<SelectionAction> {
    val restoreLabel = stringResource(R.string.action_restore)
    val deleteForeverLabel = stringResource(R.string.action_delete_forever)
    return remember(controller) {
        listOf(
            SelectionAction(Icons.Outlined.RestoreFromTrash, restoreLabel, controller::restoreSelected),
            SelectionAction(Icons.Outlined.DeleteForever, deleteForeverLabel, controller::deleteForeverSelected),
        )
    }
}

/** Unlock (back to the gallery) and permanent delete, for items in the Locked folder. */
@Composable
fun lockedActions(controller: MediaSelectionController): List<SelectionAction> {
    val unlockLabel = stringResource(R.string.action_unlock)
    val deleteLabel = stringResource(R.string.action_delete)
    return remember(controller) {
        listOf(
            SelectionAction(Icons.Outlined.LockOpen, unlockLabel, controller::unlockSelected),
            SelectionAction(Icons.Outlined.Delete, deleteLabel, controller::deleteLockedSelected),
        )
    }
}
