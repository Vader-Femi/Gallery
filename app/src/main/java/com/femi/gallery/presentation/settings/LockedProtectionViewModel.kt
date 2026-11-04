package com.femi.gallery.presentation.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.core.security.LockSession
import com.femi.gallery.data.locked.LockedBackup
import com.femi.gallery.data.locked.LockedKeyException
import com.femi.gallery.data.locked.LockedKeys
import com.femi.gallery.domain.locked.BackupCorruptException
import com.femi.gallery.domain.locked.WrongPassphraseException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import javax.inject.Inject

/** What the card of the Locked folder's protection is busy with, and what came of the last job. */
data class LockedProtectionState(
    val exporting: Pair<Int, Int>? = null,
    val importing: Int? = null,
    val result: Result? = null,
) {
    val busy: Boolean get() = exporting != null || importing != null

    sealed interface Result {
        data class Exported(val count: Int) : Result
        data class Imported(val imported: Int, val alreadyThere: Int, val failed: Int) : Result
        data object WrongPassphrase : Result
        data object Corrupt : Result
        data object KeyProblem : Result
        data object Other : Result
    }
}

@HiltViewModel
class LockedProtectionViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keys: LockedKeys,
    private val backup: LockedBackup,
    private val session: LockSession,
) : ViewModel() {

    /** Whether the folder's files can only be opened right after authenticating. */
    val authRequired: StateFlow<Boolean> = keys.authRequired

    /** True when the folder key is gone and the items can only come back from a backup. */
    val keyLost: StateFlow<Boolean> = session.keyLost

    private val _state = MutableStateFlow(LockedProtectionState())
    val state: StateFlow<LockedProtectionState> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * The person has just authenticated: opens the folder key now, while a key that requires authentication allows it,
     * so that the passphrase and the file can be chosen without the time running out.
     */
    fun authenticated() {
        viewModelScope.launch {
            withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { keys.folderKey() } }
        }
    }

    /** The person gave up before anything was done: the folder key is not kept in memory for nothing. */
    fun abandon() = releaseKey()

    private fun releaseKey() {
        if (!session.unlocked.value) keys.lock()
    }

    fun setAuthRequired(enabled: Boolean) {
        viewModelScope.launch {
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { keys.setAuthRequired(enabled) }
            }
            result.exceptionOrNull()?.let {
                Log.w(TAG, "Cannot change the authentication setting", it)
                _state.update { s -> s.copy(result = LockedProtectionState.Result.KeyProblem) }
            }
            releaseKey()
        }
    }

    fun export(destination: Uri, passphrase: CharArray) {
        if (_state.value.busy) return
        _state.update { it.copy(exporting = 0 to 0, result = null) }
        job = viewModelScope.launch {
            var written = false
            try {
                val count = context.contentResolver.openOutputStream(destination, "w")?.let { raw ->
                    BufferedOutputStream(raw, BUFFER).use { out ->
                        backup.export(out, passphrase) { done, total -> _state.update { it.copy(exporting = done to total) } }
                    }
                } ?: error("Cannot write the backup file")
                written = true
                finish(LockedProtectionState.Result.Exported(count))
            } catch (e: CancellationException) {
                withContext(NonCancellable) { deleteQuietly(destination) }
                _state.update { it.copy(exporting = null) }
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "The backup failed", e)
                if (!written) deleteQuietly(destination)
                finish(resultOf(e))
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    fun import(source: Uri, passphrase: CharArray, replaceUnreadable: Boolean) {
        if (_state.value.busy) return
        _state.update { it.copy(importing = 0, result = null) }
        job = viewModelScope.launch {
            try {
                if (replaceUnreadable) keys.resetAfterLoss()
                val result = context.contentResolver.openInputStream(source)?.let { raw ->
                    BufferedInputStream(raw, BUFFER).use { input ->
                        backup.import(input, passphrase) { done -> _state.update { it.copy(importing = done) } }
                    }
                } ?: error("Cannot read the backup file")
                finish(LockedProtectionState.Result.Imported(result.imported, result.alreadyThere, result.failed))
            } catch (e: CancellationException) {
                _state.update { it.copy(importing = null) }
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "The restore failed", e)
                finish(resultOf(e))
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun dismissResult() = _state.update { it.copy(result = null) }

    private fun finish(result: LockedProtectionState.Result) {
        _state.value = LockedProtectionState(result = result)
        releaseKey()
    }

    private fun resultOf(e: Exception): LockedProtectionState.Result = when (e) {
        is WrongPassphraseException -> LockedProtectionState.Result.WrongPassphrase
        is BackupCorruptException -> LockedProtectionState.Result.Corrupt
        is LockedKeyException -> LockedProtectionState.Result.KeyProblem
        else -> LockedProtectionState.Result.Other
    }

    private fun deleteQuietly(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
    }

    private companion object {
        const val TAG = "LockedProtection"
        const val BUFFER = 256 * 1024
    }
}
