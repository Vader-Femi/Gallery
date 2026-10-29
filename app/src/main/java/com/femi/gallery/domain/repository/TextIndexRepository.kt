package com.femi.gallery.domain.repository

import com.femi.gallery.domain.text.TextScanState
import kotlinx.coroutines.flow.StateFlow

interface TextIndexRepository {
    /** Progress of the background scan. */
    val scan: StateFlow<TextScanState>

    /**
     * Starts reading the text of photos that have not been read yet, in the background. Does nothing
     * when a scan is already running or nothing changed since the last one.
     */
    fun startScan()

    /** Whether a scan is running right now, including one that is still working out what there is to do. */
    val isRunning: Boolean

    /** Ends a running scan. What it has read and saved up to its last checkpoint is kept, and the next scan carries on from there. */
    fun stopScan()
}
