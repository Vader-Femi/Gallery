package com.femi.gallery.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.femi.gallery.R
import com.femi.gallery.domain.clip.ClipModelKind
import com.femi.gallery.domain.clip.ClipModelStatus
import com.femi.gallery.domain.clip.ClipScanStatus

/**
 * Describe-a-photo: the two model files the person installs, a switch for using them in search, and how far
 * the reading of the photos has got. The models are copied in from wherever the person has them; the app
 * never downloads anything.
 */
@Composable
fun ClipModelCard(viewModel: ClipModelViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isInitializing by viewModel.isInitializing.collectAsStateWithLifecycle()
    val card by viewModel.card.collectAsStateWithLifecycle()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val isBusy = card.busy || isInitializing
    var searchEnabled by remember { mutableStateOf(ThemePreferences.getSemanticSearch(context)) }
    var showInfoDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(stringResource(R.string.clip_info_title)) },
            text = { Text(stringResource(R.string.clip_info_body)) },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.clip_info_close))
                }
            }
        )
    }

    // ONNX files have no media type of their own, so any file may be chosen.
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.install(ClipModelKind.Image, it.toString()) }
    }
    val pickText = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.install(ClipModelKind.Text, it.toString()) }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.height(48.dp)) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.ImageSearch, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.clip_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.clip_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showInfoDialog = true }) {
                    Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.clip_info_title))
                }
            }

            Text(
                text = when {
                    isBusy -> stringResource(R.string.clip_importing)
                    status == ClipModelStatus.NotInstalled -> stringResource(R.string.clip_status_none)
                    status == ClipModelStatus.Installed -> stringResource(R.string.clip_status_ready)
                    else -> stringResource(R.string.clip_status_partial)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            card.problem?.let {
                Text(
                    text = stringResource(if (it == ClipCardState.Problem.Mismatch) R.string.clip_error_mismatch else R.string.clip_error_unusable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !isBusy, onClick = { pickImage.launch(arrayOf("*/*")) }) {
                    Text(
                        if (status == ClipModelStatus.ImageOnly || status == ClipModelStatus.Installed)
                            stringResource(R.string.clip_replace_image)
                        else
                            stringResource(R.string.clip_choose_image)
                    )
                }
                OutlinedButton(enabled = !isBusy, onClick = { pickText.launch(arrayOf("*/*")) }) {
                    Text(
                        if (status == ClipModelStatus.TextOnly || status == ClipModelStatus.Installed)
                            stringResource(R.string.clip_replace_text)
                        else
                            stringResource(R.string.clip_choose_text)
                    )
                }
            }

            if (status.isComplete) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.clip_search_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.clip_search_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = searchEnabled,
                        onCheckedChange = {
                            searchEnabled = it
                            ThemePreferences.setSemanticSearch(context, it)
                        },
                    )
                }

                if (scan.status == ClipScanStatus.Scanning && scan.total > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.clip_progress, scan.scanned, scan.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(
                            progress = { scan.scanned.toFloat() / scan.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (status != ClipModelStatus.NotInstalled) {
                Spacer(Modifier.height(0.dp))
                TextButton(enabled = !isBusy, onClick = viewModel::remove) {
                    Text(stringResource(R.string.clip_remove), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
