package com.femi.gallery.presentation.viewer

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How far the photo has been pulled away from its place, by a finger or by the Back gesture, and
 * what that does to it: it follows, shrinks, and the background and the bars fade (see
 * [ViewerDragMath]). It lives with the viewer's session rather than the screen, so that the Back
 * gesture, which is handled outside the screen, can drive the same motion.
 */
@Stable
class ViewerDragState {
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set

    /** 0 at rest, up to 1 when pulled all the way. */
    var progress by mutableFloatStateOf(0f)
        private set

    /** True while the current photo is zoomed in: a drag then moves the zoomed photo, not the viewer. */
    var zoomed by mutableStateOf(false)

    /** The height of the viewer in pixels, set by the screen. */
    var containerHeight = 0f

    private var rawOffsetY = 0f

    val scale: Float get() = ViewerDragMath.scale(progress)
    val backgroundAlpha: Float get() = ViewerDragMath.backgroundAlpha(progress)
    val chromeAlpha: Float get() = ViewerDragMath.chromeAlpha(progress)

    /** The finger moved by ([dx], [dy]) pixels. */
    fun dragBy(dx: Float, dy: Float) {
        offsetX += dx
        rawOffsetY += dy
        offsetY = ViewerDragMath.resistUp(rawOffsetY, containerHeight)
        progress = ViewerDragMath.progress(offsetY, containerHeight)
    }

    /** The Back gesture is [progress] of the way, with the finger moved ([dx], [dy]) since it began. */
    fun followBack(progress: Float, dx: Float, dy: Float) {
        offsetX = dx
        offsetY = dy
        rawOffsetY = dy
        this.progress = progress.coerceIn(0f, 1f)
    }

    /** What letting go means, with the finger moving at [velocityY] pixels per second. */
    internal fun release(velocityY: Float): ViewerDragMath.Release =
        ViewerDragMath.release(offsetY, velocityY, containerHeight)

    /** Springs the photo back to its place. */
    suspend fun restore() {
        val startX = offsetX
        val startY = offsetY
        val startProgress = progress
        if (startX != 0f || startY != 0f || startProgress != 0f) {
            animate(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) { fraction, _ ->
                offsetX = startX * fraction
                offsetY = startY * fraction
                progress = startProgress * fraction
            }
        }
        reset()
    }

    /** Puts the photo back at once. */
    fun reset() {
        offsetX = 0f
        offsetY = 0f
        progress = 0f
        rawOffsetY = 0f
        zoomed = false
    }
}

/** The viewer's drag state, for the photo pages to report that they are zoomed. Null outside the viewer. */
internal val LocalViewerDrag = compositionLocalOf<ViewerDragState?> { null }

/**
 * Places this at the dragged position and size. This is done in layout, not with a graphics layer,
 * so the photo's real bounds move with it: when the viewer closes, the photo flies back to its grid
 * cell from where it is, not from where it would be at rest.
 */
internal fun Modifier.dragTransform(drag: ViewerDragState): Modifier = layout { measurable, constraints ->
    val scale = drag.scale
    val width = (constraints.maxWidth * scale).roundToInt().coerceAtLeast(1)
    val height = (constraints.maxHeight * scale).roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(
            x = (constraints.maxWidth - width) / 2 + drag.offsetX.roundToInt(),
            y = (constraints.maxHeight - height) / 2 + drag.offsetY.roundToInt(),
        )
    }
}

/**
 * Pulling the photo up or down with one finger. The gesture is watched before the pager and the zoom
 * handler see it and only taken over once it is clearly more vertical than horizontal, so swiping to
 * the next photo, pinching, double-tapping and panning a zoomed photo all work as before.
 *
 * [onDrag] gets the movement in pixels; [onEnd] the speed (pixels per second, down is positive) when
 * the finger lifts; [onCancel] when a second finger lands.
 */
@Composable
internal fun Modifier.viewerDrag(
    enabled: () -> Boolean,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onEnd: (velocityY: Float) -> Unit,
    onCancel: () -> Unit,
): Modifier {
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnDrag by rememberUpdatedState(onDrag)
    val latestOnEnd by rememberUpdatedState(onEnd)
    val latestOnCancel by rememberUpdatedState(onCancel)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!latestEnabled()) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            val velocity = VelocityTracker()
            velocity.addPosition(down.uptimeMillis, down.position)
            var movedX = 0f
            var movedY = 0f
            var dragging = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } > 1) {
                    if (dragging) latestOnCancel()
                    return@awaitEachGesture
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed) {
                    if (dragging) latestOnEnd(velocity.calculateVelocity().y)
                    return@awaitEachGesture
                }
                if (!dragging && change.isConsumed) return@awaitEachGesture
                velocity.addPosition(change.uptimeMillis, change.position)
                val delta = change.positionChange()
                if (dragging) {
                    latestOnDrag(delta.x, delta.y)
                    change.consume()
                } else {
                    movedX += delta.x
                    movedY += delta.y
                    when {
                        abs(movedY) > slop && abs(movedY) > abs(movedX) -> {
                            dragging = true
                            latestOnDrag(movedX, movedY)
                            change.consume()
                        }
                        // Mostly sideways: the pager's.
                        abs(movedX) > slop -> return@awaitEachGesture
                    }
                }
            }
        }
    }
}
