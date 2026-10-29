package com.femi.gallery.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import com.femi.gallery.data.work.BackgroundScans
import com.femi.gallery.core.security.LockSession
import com.femi.gallery.domain.usecase.StartClipScanUseCase
import com.femi.gallery.domain.usecase.StartColorScanUseCase
import com.femi.gallery.domain.usecase.StartLabelScanUseCase
import com.femi.gallery.domain.usecase.StartLocationScanUseCase
import com.femi.gallery.domain.usecase.StartTextScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** App-wide state that screens share: whether the Locked folder is currently unlocked. */
@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val lockSession: LockSession,
    private val startLocationScan: StartLocationScanUseCase,
    private val startTextScan: StartTextScanUseCase,
    private val startColorScan: StartColorScanUseCase,
    private val startLabelScan: StartLabelScanUseCase,
    private val startClipScan: StartClipScanUseCase,
) : ViewModel() {

    /**
     * Starts the background scans of new photos: where they were taken, text, colours, labels, and embeddings.
     * Cheap when there is nothing new.
     */
    fun scanLocations() {
        startLocationScan()
        startTextScan()
        startColorScan()
        startLabelScan()
        startClipScan()
        // So that scans still working when the app is closed carry on, and new photos are read even if it is not opened.
        BackgroundScans.update(context)
    }
}
