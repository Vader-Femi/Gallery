package com.femi.gallery.presentation.viewer

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val MaxScale = 6f
private const val DoubleTapScale = 2.5f

/**
 * Pinch-to-zoom, pan and double-tap-to-zoom container.
 *
 * Single-finger drags are left alone while not zoomed, so the surrounding pager can swipe.
 * When zoomed and the image is already at a horizontal edge, drags are also handed back to
 * the pager so swiping to the next photo still works.
 *
 * [content] receives a callback to report the image's intrinsic size, which is used to limit
 * panning to the visible image instead of the whole screen.
 */
@Composable
fun ZoomableBox(
    isCurrent: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (onImageSize: (Size) -> Unit) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember { mutableStateOf(Size.Zero) }
    val scope = rememberCoroutineScope()
    // The gesture handlers below are created once, so they must read the latest callback.
    val latestOnTap by rememberUpdatedState(onTap)

    LaunchedEffect(isCurrent) {
        if (!isCurrent) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    // Tells the viewer when the current photo is zoomed in, so that dragging then pans the photo
    // instead of pulling the viewer away.
    val viewerDrag = LocalViewerDrag.current
    LaunchedEffect(viewerDrag, isCurrent) {
        if (viewerDrag == null || !isCurrent) return@LaunchedEffect
        try {
            snapshotFlow { scale > 1.02f }.collect { viewerDrag.zoomed = it }
        } finally {
            viewerDrag.zoomed = false
        }
    }

    /** Largest pan (per axis) that keeps the zoomed image covering what it covered at scale 1. */
    fun maxPan(forScale: Float): Offset {
        val cw = containerSize.width.toFloat()
        val ch = containerSize.height.toFloat()
        if (cw == 0f || ch == 0f) return Offset.Zero
        val fit = if (imageSize.width > 0f && imageSize.height > 0f) {
            min(cw / imageSize.width, ch / imageSize.height)
        } else {
            1f
        }
        val displayedW = if (imageSize.width > 0f) imageSize.width * fit else cw
        val displayedH = if (imageSize.height > 0f) imageSize.height * fit else ch
        return Offset(
            max(0f, (displayedW * forScale - cw) / 2f),
            max(0f, (displayedH * forScale - ch) / 2f),
        )
    }

    fun clampOffset(value: Offset, forScale: Float): Offset {
        val limit = maxPan(forScale)
        return Offset(value.x.coerceIn(-limit.x, limit.x), value.y.coerceIn(-limit.y, limit.y))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .tapGestures(
                onTap = { latestOnTap() },
                onDoubleTap = { tap ->
                    val startScale = scale
                    val startOffset = offset
                    val targetScale = if (startScale > 1.05f) 1f else DoubleTapScale
                    val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                    val focus = tap - center
                    // Keep the tapped point under the finger while changing scale.
                    val rawTarget = focus - (focus - startOffset) * (targetScale / startScale)
                    val targetOffset = if (targetScale == 1f) Offset.Zero else clampOffset(rawTarget, targetScale)
                    scope.launch {
                        animate(0f, 1f, animationSpec = tween(250)) { fraction, _ ->
                            scale = startScale + (targetScale - startScale) * fraction
                            offset = startOffset + (targetOffset - startOffset) * fraction
                        }
                    }
                },
            )
            .transformGestures(
                currentScale = { scale },
                currentOffset = { offset },
                containerCenter = { Offset(containerSize.width / 2f, containerSize.height / 2f) },
                onTransform = { newScale, newOffsetUnclamped ->
                    val clamped = clampOffset(newOffsetUnclamped, newScale)
                    scale = newScale
                    offset = clamped
                    // How far the requested pan overshot the allowed range (0 = fully applied).
                    newOffsetUnclamped - clamped
                },
                onGestureEnd = {
                    if (scale < 1.02f) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                },
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    ) {
        content { imageSize = it }
    }
}

private fun Modifier.tapGestures(
    onTap: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
): Modifier = pointerInput(Unit) {
    detectTapGestures(onTap = { onTap() }, onDoubleTap = onDoubleTap)
}

private fun Modifier.transformGestures(
    currentScale: () -> Float,
    currentOffset: () -> Offset,
    containerCenter: () -> Offset,
    onTransform: (newScale: Float, newOffset: Offset) -> Offset,
    onGestureEnd: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val pressedCount = event.changes.count { it.pressed }
            val scale = currentScale()
            if (pressedCount >= 2 || scale > 1f) {
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = false)
                val newScale = (scale * zoom).coerceIn(1f, MaxScale)
                val appliedZoom = newScale / scale
                val c = if (centroid.isSpecified) centroid - containerCenter() else Offset.Zero
                // Zoom around the fingers, then apply the pan.
                val requested = c - (c - currentOffset()) * appliedZoom + pan
                val overshoot = onTransform(newScale, requested)
                val handBackToPager =
                    pressedCount == 1 && abs(overshoot.x) > 0.5f && abs(pan.x) > abs(pan.y)
                if (!handBackToPager) {
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
        onGestureEnd()
    }
}
