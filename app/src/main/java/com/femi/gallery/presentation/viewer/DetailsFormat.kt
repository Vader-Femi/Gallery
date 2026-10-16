package com.femi.gallery.presentation.viewer

import java.util.Locale
import kotlin.math.roundToInt

/** 0.008333 -> "1/120s", 2.0 -> "2s", 0.5 -> "1/2s". */
fun formatExposure(seconds: Double): String =
    if (seconds >= 1.0) {
        val rounded = if (seconds % 1.0 == 0.0) seconds.toInt().toString() else String.format(Locale.US, "%.1f", seconds)
        "${rounded}s"
    } else {
        "1/${(1.0 / seconds).roundToInt()}s"
    }

/** 4080 x 3072 -> "4080 × 3072 · 12.5 MP". */
fun formatResolution(width: Int, height: Int): String {
    val megapixels = width.toLong() * height / 1_000_000.0
    val base = "$width × $height"
    return if (megapixels >= 0.1) base + String.format(Locale.US, " · %.1f MP", megapixels) else base
}

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
