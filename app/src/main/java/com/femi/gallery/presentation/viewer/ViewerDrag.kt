package com.femi.gallery.presentation.viewer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
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

    /** The width of the viewer in pixels, and where its top left corner is in the window; set by the screen. */
    var containerWidth = 0f
    var containerLeft = 0f
    var containerTop = 0f

    /** The speed (pixels per second, down is positive) the finger had when it let go; 0 for the Back gesture or button. */
    var releaseVelocityY = 0f

    /** Where the photo's page is in the window right now, or null before the screen has been measured. */
    fun photoBoundsInWindow(): PhotoRect? {
        if (containerWidth <= 0f || containerHeight <= 0f) return null
        val rect = ViewerDragMath.photoRect(containerWidth.roundToInt(), containerHeight.roundToInt(), scale, offsetX, offsetY)
        return PhotoRect(rect.left + containerLeft, rect.top + containerTop, rect.right + containerLeft, rect.bottom + containerTop)
    }

    private var rawOffsetY = 0f

    /** Replaces the scale the pull gives while the photo flies into its grid cell; null otherwise. */
    private var scaleOverride by mutableStateOf<Float?>(null)

    val scale: Float get() = scaleOverride ?: ViewerDragMath.scale(progress)

    /** True from the moment the viewer starts to close; nothing can be pulled then, and the shared bounds step aside. */
    var closing by mutableStateOf(false)

    /** While the photo flies into its cell: what of the viewer's content shows, in the container's coordinates. */
    internal var clip by mutableStateOf<PhotoRect?>(null)
        private set

    /** Whether the content can fly into a cell from here: it is measured and not zoomed in. */
    fun canFly(): Boolean = containerWidth > 0f && containerHeight > 0f && !zoomed
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

    /**
     * The photo flies into its grid [cellInWindow] from where it is now: the content of the viewer (the photo, whose
     * page is shown contained) is scaled and moved until the photo covers the cell, while a clip narrows from the
     * photo to the cell, so nothing is stretched, and the background and the bars fade. This is the real picture, moved
     * with the same state the pull uses, so it starts exactly where the finger left it. With [startsMoving] it
     * carries on at full speed and slows down into the cell; from rest it starts gently.
     */
    internal suspend fun flyTo(
        cellInWindow: PhotoRect,
        mediaWidth: Int,
        mediaHeight: Int,
        durationMillis: Int,
        startsMoving: Boolean,
    ) {
        val cell = PhotoRect(
            cellInWindow.left - containerLeft,
            cellInWindow.top - containerTop,
            cellInWindow.right - containerLeft,
            cellInWindow.bottom - containerTop,
        )
        val photo = ViewerDragMath.fitSize(containerWidth, containerHeight, mediaWidth, mediaHeight)
        val end = ViewerDragMath.flightEnd(containerWidth, containerHeight, photo, cell)
        val startScale = scale
        val startX = offsetX
        val startY = offsetY
        val startProgress = progress
        val startClip = ViewerDragMath.photoRectFor(containerWidth, containerHeight, photo, startScale, startX, startY)
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis, easing = if (startsMoving) LinearOutSlowInEasing else FastOutSlowInEasing),
        ) { fraction, _ ->
            scaleOverride = ViewerDragMath.lerp(startScale, end.scale, fraction)
            offsetX = ViewerDragMath.lerp(startX, end.offsetX, fraction)
            offsetY = ViewerDragMath.lerp(startY, end.offsetY, fraction)
            progress = ViewerDragMath.lerp(startProgress, 1f, fraction)
            clip = ViewerDragMath.lerp(startClip, cell, fraction)
        }
    }

    /** Puts the photo back at once. */
    fun reset() {
        scaleOverride = null
        clip = null
        closing = false
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

/** Cuts the viewer's content to [ViewerDragState.clip] while the photo flies into its cell. Goes outside `dragTransform`. */
internal fun Modifier.flightClip(drag: ViewerDragState): Modifier = drawWithContent {
    val clip = drag.clip
    if (clip == null) {
        drawContent()
    } else {
        clipRect(clip.left, clip.top, clip.right, clip.bottom) { this@drawWithContent.drawContent() }
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
