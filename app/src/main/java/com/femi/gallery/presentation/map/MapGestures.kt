package com.femi.gallery.presentation.map

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/**
 * Drag and pinch for the map, like `detectTransformGestures`, with two additions the map needs:
 * [onDown] is called the moment a finger touches (so a coasting map can be stopped), and when the
 * last finger lifts after a drag [onFling] gets the speed it was moving at (so the map can coast on).
 *
 * No fling is reported when the gesture ended with two fingers a moment ago: lifting two fingers from a
 * pinch never lands at the same instant, and the last finger's movement is not a throw.
 */
suspend fun PointerInputScope.detectMapGestures(
    onDown: () -> Unit,
    onGesture: (centroid: Offset, pan: Offset, zoomChange: Float) -> Unit,
    onFling: (velocity: Velocity) -> Unit,
) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        onDown()

        val tracker = VelocityTracker()
        var zoomMotion = 0f
        var panMotion = Offset.Zero
        var pastSlop = false
        var sawMultiTouch = false
        var lastMultiTouchMs = 0L
        var lastEventMs = 0L
        var cancelled = false

        do {
            val event = awaitPointerEvent()
            lastEventMs = event.changes.first().uptimeMillis
            cancelled = event.changes.any { it.isConsumed }
            if (cancelled) break

            if (event.changes.count { it.pressed } > 1) {
                sawMultiTouch = true
                lastMultiTouchMs = lastEventMs
            }

            val zoomChange = event.calculateZoom()
            val pan = event.calculatePan()
            if (!pastSlop) {
                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                zoomMotion += abs(1 - zoomChange) * centroidSize
                panMotion += pan
                if (zoomMotion > slop || panMotion.getDistance() > slop) pastSlop = true
            }
            if (pastSlop) {
                val centroid = event.calculateCentroid(useCurrent = false)
                if (zoomChange != 1f || pan != Offset.Zero) onGesture(centroid, pan, zoomChange)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
                val current = event.calculateCentroid(useCurrent = true)
                if (current != Offset.Unspecified) tracker.addPosition(lastEventMs, current)
            }
        } while (event.changes.any { it.pressed })

        val wasPinch = sawMultiTouch && lastEventMs - lastMultiTouchMs < MultiTouchGraceMs
        if (pastSlop && !cancelled && !wasPinch) {
            val velocity = tracker.calculateVelocity()
            if (velocity.x != 0f || velocity.y != 0f) onFling(velocity)
        }
    }
}

/** How long after a second finger was last down a lift still counts as the end of a pinch. */
private const val MultiTouchGraceMs = 120L
