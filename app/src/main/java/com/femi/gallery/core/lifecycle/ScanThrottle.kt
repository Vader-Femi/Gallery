package com.femi.gallery.core.lifecycle

import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

/**
 * The pause a scan takes between two batches. While one of the app's screens is on display it sleeps a little, so that
 * the scan does not take the processor from image loading and scrolling; otherwise it only yields to other coroutines.
 */
object ScanThrottle {
    const val FOREGROUND_PAUSE_MS = 50L

    suspend fun pause() {
        if (AppForeground.isInForeground) delay(FOREGROUND_PAUSE_MS) else yield()
    }
}
