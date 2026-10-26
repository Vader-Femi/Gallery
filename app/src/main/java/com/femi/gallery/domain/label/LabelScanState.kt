package com.femi.gallery.domain.label

enum class LabelScanStatus {
    Idle,
    Scanning,
    Unavailable,
}

data class LabelScanState(
    val status: LabelScanStatus = LabelScanStatus.Idle,
    val scanned: Int = 0,
    val total: Int = 0,
    val withLabels: Int = 0,
)
