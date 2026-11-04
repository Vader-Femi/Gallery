package com.femi.gallery.domain.repository

import com.femi.gallery.domain.face.FaceScanState
import kotlinx.coroutines.flow.StateFlow

interface FaceIndexRepository {
    val scan: StateFlow<FaceScanState>
    val isRunning: Boolean
    fun startScan()
    fun stopScan()
}
