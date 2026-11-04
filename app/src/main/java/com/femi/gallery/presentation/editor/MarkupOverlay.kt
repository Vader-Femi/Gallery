package com.femi.gallery.presentation.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import com.femi.gallery.core.graphics.MarkupRenderer
import com.femi.gallery.domain.edit.MarkupItem
import com.femi.gallery.domain.edit.MarkupMath
import com.femi.gallery.domain.edit.MarkupPoint
import com.femi.gallery.domain.edit.MarkupStroke

/**
 * Draws the markup on the photo and, when [interactive], takes the touches for it: in [MarkupMode.Draw] a finger draws a
 * line; otherwise a finger on a text or sticker chooses it and moves it, two fingers scale and turn it, and a tap on
 * nothing lets go of it. It covers the same bounds as the photo; the photo itself is fitted inside, like the preview.
 */
@Composable
fun MarkupOverlay(
    imageWidth: Int,
    imageHeight: Int,
    items: List<MarkupItem>,
    selectedId: Long?,
    interactive: Boolean,
    mode: MarkupMode,
    penColor: Int,
    penWidth: Float,
    highlighter: Boolean,
    onCommitStroke: (points: List<MarkupPoint>) -> Unit,
    onSelect: (Long?) -> Unit,
    onBeginGesture: () -> Unit,
    onTransform: (id: Long, dx: Float, dy: Float, zoom: Float, rotation: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drawing by remember { mutableStateOf<List<MarkupPoint>>(emptyList()) }
    val currentItems by rememberUpdatedState(items)
    val currentSelected by rememberUpdatedState(selectedId)
    val currentOnCommit by rememberUpdatedState(onCommitStroke)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnBegin by rememberUpdatedState(onBeginGesture)
    val currentOnTransform by rememberUpdatedState(onTransform)
    val slop = LocalViewConfiguration.current.touchSlop

    val touch = when {
        !interactive -> Modifier
        mode == MarkupMode.Draw -> Modifier
            .pointerInput(imageWidth, imageHeight) {
                detectTapGestures { offset ->
                    // A tap leaves a dot.
                    val image = fitRect(size.width.toFloat(), size.height.toFloat(), imageWidth, imageHeight)
                    toPoint(offset, image.left, image.top, image.width, image.height)?.let { currentOnCommit(listOf(it)) }
                }
            }
            .pointerInput(imageWidth, imageHeight) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val image = fitRect(size.width.toFloat(), size.height.toFloat(), imageWidth, imageHeight)
                        drawing = listOfNotNull(toPoint(offset, image.left, image.top, image.width, image.height))
                    },
                    onDrag = { change, _ ->
                        val image = fitRect(size.width.toFloat(), size.height.toFloat(), imageWidth, imageHeight)
                        toPoint(change.position, image.left, image.top, image.width, image.height)?.let { drawing = drawing + it }
                        change.consume()
                    },
                    onDragEnd = {
                        val points = drawing
                        drawing = emptyList()
                        if (points.isNotEmpty()) currentOnCommit(points)
                    },
                    onDragCancel = { drawing = emptyList() },
                )
            }

        else -> Modifier.pointerInput(imageWidth, imageHeight, slop) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                val image = fitRect(size.width.toFloat(), size.height.toFloat(), imageWidth, imageHeight)
                val point = toPoint(down.position, image.left, image.top, image.width, image.height)
                val hit = point?.let { MarkupMath.itemAt(currentItems, it.x, it.y, image.width, image.height) }
                var target = hit
                if (hit != null && hit != currentSelected) currentOnSelect(hit)
                var begun = false
                var moved = false
                var travelled = Offset.Zero
                do {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val pan = event.calculatePan()
                    val zoom = event.calculateZoom()
                    val rotation = event.calculateRotation()
                    travelled += pan
                    if (travelled.getDistance() > slop) moved = true
                    // Two fingers act on the chosen item even if the first one is on empty space.
                    if (target == null && event.changes.count { it.pressed } > 1) target = currentSelected
                    val id = target
                    if (id != null && moved && (pan != Offset.Zero || zoom != 1f || rotation != 0f)) {
                        if (!begun) {
                            begun = true
                            currentOnBegin()
                        }
                        currentOnTransform(id, pan.x / image.width, pan.y / image.height, zoom, rotation)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                if (hit == null && !moved) currentOnSelect(null)
            }
        }
    }

    Canvas(modifier = modifier.then(touch)) {
        val image = fitRect(size.width, size.height, imageWidth, imageHeight)
        val shown = if (drawing.isEmpty()) {
            items
        } else {
            items + MarkupStroke(Long.MAX_VALUE, drawing, penColor, penWidth, highlighter)
        }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.translate(image.left, image.top)
            MarkupRenderer.draw(native, image.width, image.height, shown, if (interactive) selectedId else null)
            native.restore()
        }
    }
}

/** A touch position as a point on the photo (fractions), or null when it is outside it. */
private fun toPoint(offset: Offset, left: Float, top: Float, width: Float, height: Float): MarkupPoint? {
    if (width <= 0f || height <= 0f) return null
    val x = (offset.x - left) / width
    val y = (offset.y - top) / height
    return if (x in 0f..1f && y in 0f..1f) MarkupPoint(x, y) else null
}
