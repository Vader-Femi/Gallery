package com.femi.gallery.domain.clip

enum class ClipScanStatus {
    Idle,
    Scanning,
    Unavailable,
}

data class ClipScanState(
    val status: ClipScanStatus = ClipScanStatus.Idle,
    val scanned: Int = 0,
    val total: Int = 0,
    val withEmbeddings: Int = 0,
)
