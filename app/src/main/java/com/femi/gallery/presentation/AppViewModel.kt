package com.femi.gallery.presentation

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.data.work.BackgroundScans
import com.femi.gallery.core.security.LockSession
import com.femi.gallery.domain.usecase.StartClipScanUseCase
import com.femi.gallery.domain.usecase.StartColorScanUseCase
import com.femi.gallery.domain.usecase.StartFaceScanUseCase
import com.femi.gallery.domain.usecase.StartLabelScanUseCase
import com.femi.gallery.domain.usecase.StartLocationScanUseCase
import com.femi.gallery.domain.usecase.StartTextScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private val startFaceScan: StartFaceScanUseCase,
) : ViewModel() {

    private var scanJob: Job? = null
    private var lastScanTriggerTime = 0L

    /**
     * Starts the background scans of new photos after an initial grace period off the main thread.
     * Deferring off the immediate foreground transition allows the UI to compose,
     * load initial thumbnails, and handle initial interactions smoothly without CPU contention.
     */
    fun scanLocations() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastScanTriggerTime < MIN_SCAN_INTERVAL_MS) {
            return
        }
        lastScanTriggerTime = now
        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.Default) {
            delay(STARTUP_DELAY_MS)
            startLocationScan()
            startTextScan()
            startColorScan()
            startLabelScan()
            startClipScan()
            startFaceScan()
            BackgroundScans.update(context)
        }
    }

    private companion object {
        const val STARTUP_DELAY_MS = 2_500L
        const val MIN_SCAN_INTERVAL_MS = 30_000L
    }
}
