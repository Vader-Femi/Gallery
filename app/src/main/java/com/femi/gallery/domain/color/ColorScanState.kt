package com.femi.gallery.domain.color

enum class ColorScanStatus {
    Idle,
    Scanning,
    Unavailable,
}

data class ColorScanState(
    val status: ColorScanStatus = ColorScanStatus.Idle,
    val scanned: Int = 0,
    val total: Int = 0,
    val withColor: Int = 0,
)
