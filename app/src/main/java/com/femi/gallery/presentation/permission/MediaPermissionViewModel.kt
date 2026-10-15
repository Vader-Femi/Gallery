package com.femi.gallery.presentation.permission

import androidx.lifecycle.ViewModel
import com.femi.gallery.core.permission.MediaPermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class MediaPermissionUiState(
    val hasAccess: Boolean,
    /** True once the system will no longer show the permission dialog. */
    val permanentlyDenied: Boolean = false,
)

@HiltViewModel
class MediaPermissionViewModel @Inject constructor(
    private val checker: MediaPermissionChecker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaPermissionUiState(hasAccess = checker.hasMediaAccess()))
    val uiState: StateFlow<MediaPermissionUiState> = _uiState.asStateFlow()

    /** Call on resume: the user may have granted access from system settings. */
    fun refresh() {
        _uiState.update { it.copy(hasAccess = checker.hasMediaAccess()) }
    }

    fun onPermissionResult(permanentlyDenied: Boolean) {
        val hasAccess = checker.hasMediaAccess()
        _uiState.update {
            it.copy(hasAccess = hasAccess, permanentlyDenied = !hasAccess && permanentlyDenied)
        }
    }
}
