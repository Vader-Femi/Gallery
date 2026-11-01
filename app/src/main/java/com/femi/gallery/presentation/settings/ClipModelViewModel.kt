package com.femi.gallery.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.data.work.BackgroundScans
import com.femi.gallery.domain.clip.ClipModelKind
import com.femi.gallery.domain.clip.ClipModelStatus
import com.femi.gallery.domain.clip.ClipScanState
import com.femi.gallery.domain.repository.ClipIndexRepository
import com.femi.gallery.domain.repository.ClipInstallResult
import com.femi.gallery.domain.repository.ClipModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the describe-a-photo card has to tell the person besides the status of the models. */
data class ClipCardState(
    /** A model is being copied in and checked. */
    val busy: Boolean = false,
    /** Why the last file was refused, if it was. */
    val problem: Problem? = null,
) {
    enum class Problem { Unusable, Mismatch }
}

@HiltViewModel
class ClipModelViewModel @Inject constructor(
    private val manager: ClipModelManager,
    private val clip: ClipIndexRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val status: StateFlow<ClipModelStatus> = manager.status
    val isInitializing: StateFlow<Boolean> = manager.isInitializing
    val scan: StateFlow<ClipScanState> = clip.scan

    private val _card = MutableStateFlow(ClipCardState())
    val card: StateFlow<ClipCardState> = _card.asStateFlow()

    /** Installs the file the person chose; when both models are in, starts reading the photos. */
    fun install(kind: ClipModelKind, source: String) {
        if (_card.value.busy) return
        _card.value = ClipCardState(busy = true)
        viewModelScope.launch {
            val result = manager.install(kind, source)
            _card.update {
                it.copy(
                    busy = false,
                    problem = when (result) {
                        ClipInstallResult.Installed -> null
                        ClipInstallResult.Unusable -> ClipCardState.Problem.Unusable
                        ClipInstallResult.Mismatch -> ClipCardState.Problem.Mismatch
                    },
                )
            }
            if (result == ClipInstallResult.Installed && manager.status.value.isComplete) {
                clip.startScan()
                BackgroundScans.update(context) // so that it carries on if the app is closed
            }
        }
    }

    fun remove() {
        viewModelScope.launch {
            clip.stopScan()
            manager.remove()
            _card.value = ClipCardState()
        }
    }
}
