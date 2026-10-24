package com.femi.gallery.presentation.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

private const val ZoomStep = 1.15f

/**
 * Two-finger pinch changes the number of columns. Runs in the Initial pass and consumes
 * multi-touch events so the grid doesn't scroll while pinching.
 */
fun Modifier.pinchToChangeColumns(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var accumulated = 1f
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                accumulated *= event.calculateZoom()
                event.changes.forEach { it.consume() }
                when {
                    accumulated > ZoomStep -> {
                        onZoomIn()
                        accumulated = 1f
                    }
                    accumulated < 1f / ZoomStep -> {
                        onZoomOut()
                        accumulated = 1f
                    }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
