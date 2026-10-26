package com.femi.gallery.domain.repository

import com.femi.gallery.domain.clip.ClipScanState
import kotlinx.coroutines.flow.StateFlow

interface ClipIndexRepository {
    val scan: StateFlow<ClipScanState>
    fun startScan()
}
