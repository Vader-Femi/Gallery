package com.femi.gallery.domain.face

enum class FaceScanStatus {
    Idle,
    Scanning,
}

data class FaceScanState(
    val scanned: Int = 0,
    val total: Int = 0,
    val status: FaceScanStatus = FaceScanStatus.Idle,
)
