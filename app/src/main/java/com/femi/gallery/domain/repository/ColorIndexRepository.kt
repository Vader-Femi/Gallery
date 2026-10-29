package com.femi.gallery.domain.repository

import com.femi.gallery.domain.color.ColorScanState
import kotlinx.coroutines.flow.StateFlow

interface ColorIndexRepository {
    val scan: StateFlow<ColorScanState>
    fun startScan()

    /** Whether a scan is running right now, including one that is still working out what there is to do. */
    val isRunning: Boolean

    /** Ends a running scan. What it has read and saved up to its last checkpoint is kept, and the next scan carries on from there. */
    fun stopScan()
}
