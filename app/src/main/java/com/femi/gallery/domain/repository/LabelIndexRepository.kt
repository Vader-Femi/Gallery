package com.femi.gallery.domain.repository

import com.femi.gallery.domain.label.LabelScanState
import kotlinx.coroutines.flow.StateFlow

interface LabelIndexRepository {
    val scan: StateFlow<LabelScanState>
    fun startScan()
}
