package com.femi.gallery.presentation.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.femi.gallery.R
import com.femi.gallery.domain.locked.PassphrasePolicy
import com.femi.gallery.presentation.common.findActivity
import com.femi.gallery.presentation.locked.BiometricAuth
import java.time.LocalDate

/** What the person is in the middle of choosing. */
private enum class Step { None, ConfirmAuthOn, ExportPassphrase, ExportFile, ImportFile, ImportPassphrase, ConfirmReplace }

/**
 * The protection of the Locked folder: the switch that makes opening its files need authentication, and the encrypted
 * backup (export to a file chosen by the person, restore from one). Every action starts with the phone's own
 * authentication, as opening the folder does.
 */
@Composable
fun LockedProtectionCard(viewModel: LockedProtectionViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    val available = BiometricAuth.isAvailable(context)
    val authRequired by viewModel.authRequired.collectAsStateWithLifecycle()
    val keyLost by viewModel.keyLost.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var step by remember { mutableStateOf(Step.None) }
    var passphrase by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var backupUri by remember { mutableStateOf<Uri?>(null) }

    val promptTitle = stringResource(R.string.locked_protect_prompt_title)
    val promptSubtitle = stringResource(R.string.locked_prompt_subtitle)
    fun authenticate(then: () -> Unit) {
        if (activity != null) {
            BiometricAuth.authenticate(activity, promptTitle, promptSubtitle, onSuccess = {
                viewModel.authenticated()
                then()
            })
        }
    }
    fun reset() {
        step = Step.None
        passphrase = ""
        repeat = ""
        backupUri = null
    }
    fun giveUp() {
        viewModel.abandon()
        reset()
    }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) {
            giveUp()
        } else {
            viewModel.export(uri, passphrase.toCharArray())
            reset()
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            giveUp()
        } else {
            backupUri = uri
            step = Step.ImportPassphrase
        }
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
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.locked_protect_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(if (available) R.string.locked_protect_summary else R.string.locked_protect_no_lock),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (keyLost) {
                Text(
                    text = stringResource(R.string.locked_key_lost_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.locked_protect_auth_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.locked_protect_auth_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = authRequired,
                    enabled = available && !state.busy && !keyLost,
                    onCheckedChange = { enable ->
                        if (enable) step = Step.ConfirmAuthOn else authenticate { viewModel.setAuthRequired(false) }
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    enabled = available && !state.busy && !keyLost,
                    onClick = { authenticate { step = Step.ExportPassphrase } },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.locked_backup_export)) }
                OutlinedButton(
                    enabled = available && !state.busy,
                    onClick = { authenticate { step = if (keyLost) Step.ConfirmReplace else Step.ImportFile.also { openFile.launch(arrayOf("*/*")) } } },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.locked_backup_import)) }
            }
        }
    }

    when (step) {
        Step.ConfirmAuthOn -> AlertDialog(
            onDismissRequest = ::reset,
            title = { Text(stringResource(R.string.locked_protect_auth_warning_title)) },
            text = { Text(stringResource(R.string.locked_protect_auth_warning_body)) },
            confirmButton = {
                TextButton(onClick = {
                    reset()
                    authenticate { viewModel.setAuthRequired(true) }
                }) { Text(stringResource(R.string.locked_protect_auth_turn_on)) }
            },
            dismissButton = { TextButton(onClick = ::reset) { Text(stringResource(R.string.picker_cancel)) } },
        )

        Step.ConfirmReplace -> AlertDialog(
            onDismissRequest = ::giveUp,
            title = { Text(stringResource(R.string.locked_backup_lost_confirm_title)) },
            text = { Text(stringResource(R.string.locked_backup_lost_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    step = Step.ImportFile
                    openFile.launch(arrayOf("*/*"))
                }) { Text(stringResource(R.string.locked_backup_continue)) }
            },
            dismissButton = { TextButton(onClick = ::giveUp) { Text(stringResource(R.string.picker_cancel)) } },
        )

        Step.ExportPassphrase, Step.ImportPassphrase -> {
            val exporting = step == Step.ExportPassphrase
            val acceptable = PassphrasePolicy.isAcceptable(passphrase)
            val matches = passphrase == repeat
            AlertDialog(
                onDismissRequest = ::giveUp,
                title = {
                    Text(stringResource(if (exporting) R.string.locked_backup_pass_title_export else R.string.locked_backup_pass_title_import))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (exporting) {
                            Text(stringResource(R.string.locked_backup_pass_hint), style = MaterialTheme.typography.bodyMedium)
                        }
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = { Text(stringResource(R.string.locked_backup_pass_field)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (exporting) {
                            OutlinedTextField(
                                value = repeat,
                                onValueChange = { repeat = it },
                                label = { Text(stringResource(R.string.locked_backup_pass_confirm)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                isError = repeat.isNotEmpty() && !matches,
                                supportingText = {
                                    when {
                                        passphrase.isNotEmpty() && !acceptable -> Text(stringResource(R.string.locked_backup_pass_weak))
                                        repeat.isNotEmpty() && !matches -> Text(stringResource(R.string.locked_backup_pass_mismatch))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = if (exporting) acceptable && matches else passphrase.isNotEmpty(),
                        onClick = {
                            if (exporting) {
                                step = Step.ExportFile
                                createFile.launch("gallery-locked-backup-${LocalDate.now()}.glb")
                            } else {
                                val source = backupUri
                                if (source != null) {
                                    viewModel.import(source, passphrase.toCharArray(), replaceUnreadable = keyLost)
                                }
                                reset()
                            }
                        },
                    ) { Text(stringResource(R.string.locked_backup_continue)) }
                },
                dismissButton = { TextButton(onClick = ::giveUp) { Text(stringResource(R.string.picker_cancel)) } },
            )
        }

        else -> Unit
    }

    state.exporting?.let { (done, total) ->
        ProgressDialog(
            text = stringResource(R.string.locked_backup_exporting, done, total),
            progress = if (total > 0) done / total.toFloat() else null,
            onCancel = viewModel::cancel,
        )
    }
    state.importing?.let { done ->
        ProgressDialog(text = stringResource(R.string.locked_backup_importing, done), progress = null, onCancel = viewModel::cancel)
    }
    state.result?.let { result ->
        val message = when (result) {
            is LockedProtectionState.Result.Exported -> stringResource(R.string.locked_backup_done_export, result.count)
            is LockedProtectionState.Result.Imported ->
                stringResource(R.string.locked_backup_done_import, result.imported, result.alreadyThere, result.failed)
            LockedProtectionState.Result.WrongPassphrase -> stringResource(R.string.locked_backup_failed_wrong)
            LockedProtectionState.Result.Corrupt -> stringResource(R.string.locked_backup_failed_corrupt)
            LockedProtectionState.Result.KeyProblem -> stringResource(R.string.locked_backup_failed_key)
            LockedProtectionState.Result.Other -> stringResource(R.string.locked_backup_failed_other)
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissResult,
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissResult) { Text(stringResource(R.string.locked_ok)) } },
        )
    }
}

@Composable
private fun ProgressDialog(text: String, progress: Float?, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(text) },
        text = {
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.picker_cancel)) } },
    )
}
