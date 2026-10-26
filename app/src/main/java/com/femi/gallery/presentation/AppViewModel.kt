package com.femi.gallery.presentation

import androidx.lifecycle.ViewModel
import com.femi.gallery.core.security.LockSession
import com.femi.gallery.domain.usecase.StartLocationScanUseCase
import com.femi.gallery.domain.usecase.StartTextScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** App-wide state that screens share: whether the Locked folder is currently unlocked. */
@HiltViewModel
class AppViewModel @Inject constructor(
    val lockSession: LockSession,
    private val startLocationScan: StartLocationScanUseCase,
    private val startTextScan: StartTextScanUseCase,
) : ViewModel() {

    /**
     * Starts the background scans of new photos: where they were taken, and the text written in them.
     * Cheap when there is nothing new.
     */
    fun scanLocations() {
        startLocationScan()
        startTextScan()
    }
}
