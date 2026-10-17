package com.femi.gallery.domain.edit

import kotlin.math.max
import kotlin.math.min

enum class CropCorner(val isLeft: Boolean, val isTop: Boolean) {
    TopLeft(true, true),
    TopRight(false, true),
    BottomLeft(true, false),
    BottomRight(false, false),
}

/** Geometry for the crop tool. Rectangles are fractions of the image, see [CropRect]. */
object CropMath {

    /** The crop can't get smaller than this fraction of the image in either direction. */
    const val MIN_SIZE = 0.1f

    /**
     * The largest centered crop with the given width/height [aspect]; null means no restriction,
     * which is the whole image.
     */
    fun fitAspect(imageWidth: Int, imageHeight: Int, aspect: Float?): CropRect {
        if (aspect == null || imageWidth <= 0 || imageHeight <= 0) return CropRect.Full
        val imageAspect = imageWidth.toFloat() / imageHeight
        val width: Float
        val height: Float
        if (aspect > imageAspect) {
            width = 1f
            height = imageAspect / aspect
        } else {
            height = 1f
            width = aspect / imageAspect
        }
        val left = (1f - width) / 2f
        val top = (1f - height) / 2f
        return CropRect(left, top, left + width, top + height)
    }

    /** Slides the whole crop by a fraction of the image, keeping it inside the image. */
    fun move(rect: CropRect, dx: Float, dy: Float): CropRect {
        val newLeft = (rect.left + dx).coerceIn(0f, 1f - rect.width)
        val newTop = (rect.top + dy).coerceIn(0f, 1f - rect.height)
        return CropRect(newLeft, newTop, newLeft + rect.width, newTop + rect.height)
    }

    /**
     * Drags one corner by ([dx], [dy]) while the opposite corner stays put. With an [aspect]
     * (width/height in pixels) the crop keeps that shape.
     */
    fun dragCorner(
        rect: CropRect,
        corner: CropCorner,
        dx: Float,
        dy: Float,
        imageWidth: Int,
        imageHeight: Int,
        aspect: Float? = null,
    ): CropRect {
        val anchorX = if (corner.isLeft) rect.right else rect.left
        val anchorY = if (corner.isTop) rect.bottom else rect.top
        val dirX = if (corner.isLeft) -1f else 1f
        val dirY = if (corner.isTop) -1f else 1f
        val roomX = if (corner.isLeft) anchorX else 1f - anchorX
        val roomY = if (corner.isTop) anchorY else 1f - anchorY

        val draggedX = (if (corner.isLeft) rect.left else rect.right) + dx
        val draggedY = (if (corner.isTop) rect.top else rect.bottom) + dy
        var width = ((draggedX - anchorX) * dirX).coerceIn(min(MIN_SIZE, roomX), roomX)
        var height = ((draggedY - anchorY) * dirY).coerceIn(min(MIN_SIZE, roomY), roomY)

        if (aspect != null && imageWidth > 0 && imageHeight > 0) {
            val pixelWidth = width * imageWidth
            val pixelHeight = height * imageHeight
            if (pixelWidth / max(pixelHeight, 1e-6f) > aspect) {
                width = pixelHeight * aspect / imageWidth
            } else {
                height = pixelWidth / aspect / imageHeight
            }
            // Shrink (never grow past the image) so both sides fit in the room available.
            val shrink = min(1f, min(roomX / width, roomY / height))
            width *= shrink
            height *= shrink
            // And grow back up to the minimum size if the drag made it too small.
            val grow = max(1f, max(MIN_SIZE / width, MIN_SIZE / height))
            val maxGrow = min(roomX / width, roomY / height)
            val factor = min(grow, maxGrow)
            width *= factor
            height *= factor
        }

        val left = if (corner.isLeft) anchorX - width else anchorX
        val top = if (corner.isTop) anchorY - height else anchorY
        return CropRect(left, top, left + width, top + height)
    }
}
