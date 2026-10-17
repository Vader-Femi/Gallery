package com.femi.gallery.presentation.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Everything a screen needs to run selection actions: the system confirmation dialogs plus the
 * album picker for move and copy. [onSuccess] runs after an action finished (e.g. to leave
 * selection mode).
 */
@Composable
fun SelectionHost(
    controller: MediaSelectionController,
    onSuccess: () -> Unit,
) {
    WriteRequestHandler(batches = controller.batches, onSuccess = onSuccess)

    val picker by controller.picker.collectAsStateWithLifecycle()
    picker?.let { request ->
        AlbumPickerSheet(
            mode = request.mode,
            onChoose = controller::choosePath,
            onDismiss = controller::dismissPicker,
        )
    }
}
