package com.femi.gallery.presentation.selection

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
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
        SelectionAction(Icons.Outlined.Lock, stringResource(R.string.action_lock)) {
            lockWithScreenLockCheck(context) { controller.lockSelected() }
        },
    )
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
fun recycleBinActions(controller: MediaSelectionController): List<SelectionAction> = listOf(
    SelectionAction(Icons.Outlined.RestoreFromTrash, stringResource(R.string.action_restore), controller::restoreSelected),
    SelectionAction(Icons.Outlined.DeleteForever, stringResource(R.string.action_delete_forever), controller::deleteForeverSelected),
)

/** Unlock (back to the gallery) and permanent delete, for items in the Locked folder. */
@Composable
fun lockedActions(controller: MediaSelectionController): List<SelectionAction> = listOf(
    SelectionAction(Icons.Outlined.LockOpen, stringResource(R.string.action_unlock), controller::unlockSelected),
    SelectionAction(Icons.Outlined.Delete, stringResource(R.string.action_delete), controller::deleteLockedSelected),
)
