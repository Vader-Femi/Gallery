package com.femi.gallery.domain.repository

import com.femi.gallery.domain.clip.ClipScanState
import kotlinx.coroutines.flow.StateFlow

interface ClipIndexRepository {
    val scan: StateFlow<ClipScanState>
    fun startScan()

    /** Whether a scan is running right now, including one that is still working out what there is to do. */
    val isRunning: Boolean

    /** Ends a running scan; what it has read and saved up to its last checkpoint is kept. */
    fun stopScan()
}
