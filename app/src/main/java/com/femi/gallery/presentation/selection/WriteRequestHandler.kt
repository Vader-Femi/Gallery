package com.femi.gallery.presentation.selection

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.femi.gallery.R
import kotlinx.coroutines.flow.Flow

/**
 * Shows the system confirmation dialogs for every [WriteBatch] emitted by [batches], one after
 * another. [onSuccess] runs once all dialogs of a batch were approved (e.g. to leave selection mode).
 */
@Composable
fun WriteRequestHandler(
    batches: Flow<WriteBatch>,
    onSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val latestOnSuccess by rememberUpdatedState(onSuccess)

    // The batch being confirmed and the index of the dialog currently on screen.
    var inFlight by remember { mutableStateOf<Pair<WriteBatch, Int>?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val (batch, index) = inFlight ?: return@rememberLauncherForActivityResult
        when {
            result.resultCode != Activity.RESULT_OK -> inFlight = null // user declined: stop
            index + 1 < batch.requests.size -> inFlight = batch to index + 1
            else -> {
                inFlight = null
                Toast.makeText(context, successMessage(context, batch), Toast.LENGTH_SHORT).show()
                latestOnSuccess()
            }
        }
    }

    LaunchedEffect(batches) {
        batches.collect { inFlight = it to 0 }
    }

    LaunchedEffect(inFlight) {
        val (batch, index) = inFlight ?: return@LaunchedEffect
        launcher.launch(IntentSenderRequest.Builder(batch.requests[index].intentSender).build())
    }
}

private fun successMessage(context: android.content.Context, batch: WriteBatch): String = when (batch.kind) {
    WriteKind.Trash -> context.resources.getQuantityString(R.plurals.toast_trashed, batch.itemCount, batch.itemCount)
    WriteKind.Favorite -> context.getString(R.string.toast_favorited)
    WriteKind.Unfavorite -> context.getString(R.string.toast_unfavorited)
    WriteKind.Restore -> context.resources.getQuantityString(R.plurals.toast_restored, batch.itemCount, batch.itemCount)
    WriteKind.DeleteForever -> context.resources.getQuantityString(R.plurals.toast_deleted_forever, batch.itemCount, batch.itemCount)
}
