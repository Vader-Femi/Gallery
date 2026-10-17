package com.femi.gallery.presentation.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.femi.gallery.domain.edit.CropCorner
import com.femi.gallery.domain.edit.CropRect
import kotlin.math.min

private sealed interface DragTarget {
    data class Corner(val corner: CropCorner) : DragTarget
    data object Move : DragTarget
}

/**
 * The crop box drawn over the photo: dimmed outside, a grid inside, handles on the corners.
 * Drag a corner to resize or the inside to move. It must fill exactly the same area as the
 * (fit-scaled) photo it sits on, which is how drags are converted back to fractions of the image.
 */
@Composable
fun CropOverlay(
    imageWidth: Int,
    imageHeight: Int,
    crop: CropRect,
    onDragCorner: (CropCorner, dx: Float, dy: Float) -> Unit,
    onMove: (dx: Float, dy: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentCrop by rememberUpdatedState(crop)
    val currentOnDragCorner by rememberUpdatedState(onDragCorner)
    val currentOnMove by rememberUpdatedState(onMove)
    val density = LocalDensity.current
    val grabRadius = with(density) { 32.dp.toPx() }

    Canvas(
        modifier = modifier.pointerInput(imageWidth, imageHeight) {
            var target: DragTarget? = null
            detectDragGestures(
                onDragStart = { offset ->
                    val image = fitRect(size.width.toFloat(), size.height.toFloat(), imageWidth, imageHeight)
                    target = hitTest(offset, cropToPixels(image, currentCrop), grabRadius)
                },
                onDragEnd = { target = null },
                onDragCancel = { target = null },
            ) { change, drag ->
                change.consume()
                val image = fitRect(size.width.toFloat(), size.height.toFloat(), imageWidth, imageHeight)
                val dx = drag.x / image.width
                val dy = drag.y / image.height
                when (val current = target) {
                    is DragTarget.Corner -> currentOnDragCorner(current.corner, dx, dy)
                    DragTarget.Move -> currentOnMove(dx, dy)
                    null -> Unit
                }
            }
        },
    ) {
        val image = fitRect(size.width, size.height, imageWidth, imageHeight)
        val box = cropToPixels(image, currentCrop)
        val dim = Color.Black.copy(alpha = 0.6f)

        // Dim everything outside the crop box.
        drawRect(dim, Offset(image.left, image.top), Size(image.width, box.top - image.top))
        drawRect(dim, Offset(image.left, box.bottom), Size(image.width, image.bottom - box.bottom))
        drawRect(dim, Offset(image.left, box.top), Size(box.left - image.left, box.height))
        drawRect(dim, Offset(box.right, box.top), Size(image.right - box.right, box.height))

        // Border and rule-of-thirds grid.
        drawRect(Color.White, box.topLeft, box.size, style = Stroke(width = 2.dp.toPx()))
        val grid = Color.White.copy(alpha = 0.5f)
        for (i in 1..2) {
            val x = box.left + box.width * i / 3f
            val y = box.top + box.height * i / 3f
            drawLine(grid, Offset(x, box.top), Offset(x, box.bottom), strokeWidth = 1.dp.toPx())
            drawLine(grid, Offset(box.left, y), Offset(box.right, y), strokeWidth = 1.dp.toPx())
        }

        // Corner handles.
        CropCorner.entries.forEach { corner ->
            drawCircle(Color.White, radius = 8.dp.toPx(), center = cornerPoint(box, corner))
        }
    }
}

/** Where a photo of the given size is drawn when fitted and centered in the container. */
private fun fitRect(containerWidth: Float, containerHeight: Float, imageWidth: Int, imageHeight: Int): Rect {
    val scale = min(containerWidth / imageWidth, containerHeight / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    val left = (containerWidth - width) / 2f
    val top = (containerHeight - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun cropToPixels(image: Rect, crop: CropRect): Rect = Rect(
    left = image.left + crop.left * image.width,
    top = image.top + crop.top * image.height,
    right = image.left + crop.right * image.width,
    bottom = image.top + crop.bottom * image.height,
)

private fun cornerPoint(box: Rect, corner: CropCorner): Offset = Offset(
    if (corner.isLeft) box.left else box.right,
    if (corner.isTop) box.top else box.bottom,
)

private fun hitTest(touch: Offset, box: Rect, grabRadius: Float): DragTarget? {
    val nearest = CropCorner.entries.minByOrNull { (cornerPoint(box, it) - touch).getDistance() }
    if (nearest != null && (cornerPoint(box, nearest) - touch).getDistance() <= grabRadius) {
        return DragTarget.Corner(nearest)
    }
    return if (box.contains(touch)) DragTarget.Move else null
}
