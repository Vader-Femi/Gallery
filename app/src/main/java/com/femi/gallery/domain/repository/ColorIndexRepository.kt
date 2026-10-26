package com.femi.gallery.domain.repository

import com.femi.gallery.domain.color.ColorScanState
import kotlinx.coroutines.flow.StateFlow

interface ColorIndexRepository {
    val scan: StateFlow<ColorScanState>
    fun startScan()
}
