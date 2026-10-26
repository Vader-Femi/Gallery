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
}
