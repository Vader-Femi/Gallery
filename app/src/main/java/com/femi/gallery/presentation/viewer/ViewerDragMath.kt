package com.femi.gallery.presentation.viewer

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The numbers behind dragging a photo away: how far it has been pulled, how small it gets, how the
 * background and the bars fade, and what letting go means. Pure, so it can be tested.
 */
/** A rectangle in pixels, as the photo's page is placed on screen. */
internal data class PhotoRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/** The size in pixels of a photo shown whole (contained). */
internal data class PhotoSize(val width: Float, val height: Float)

/** Where the viewer's content has to be (scale, and offset of its centre) for the photo to fill a grid cell. */
internal data class FlightEnd(val scale: Float, val offsetX: Float, val offsetY: Float)

internal object ViewerDragMath {
    /**
     * Where the photo's page is on screen while it is pulled: the container [containerWidth] by [containerHeight],
     * shrunk by [scale] about its centre, then moved by ([offsetX], [offsetY]). The same arithmetic as `dragTransform`
     * places it with, so the closing flight can start from exactly there.
     */
    fun photoRect(containerWidth: Int, containerHeight: Int, scale: Float, offsetX: Float, offsetY: Float): PhotoRect {
        val width = (containerWidth * scale).roundToInt().coerceAtLeast(1)
        val height = (containerHeight * scale).roundToInt().coerceAtLeast(1)
        val x = (containerWidth - width) / 2 + offsetX.roundToInt()
        val y = (containerHeight - height) / 2 + offsetY.roundToInt()
        return PhotoRect(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())
    }

    /** At full Back progress the photo has moved this fraction of the viewer's width toward the side the swipe went. */
    const val BACK_EDGE_SHIFT_FRACTION = 0.08f

    /**
     * How far the Back gesture moves the photo sideways on top of the finger: with the swipe, so a swipe from the
     * left edge carries it to the right and one from the right edge to the left, as the system's own back animation
     * moves a window. Without it an edge swipe, where the finger hardly moves up or down, barely moves the photo.
     */
    fun backEdgeShift(fromRightEdge: Boolean, progress: Float, containerWidth: Float): Float {
        val direction = if (fromRightEdge) -1f else 1f
        return direction * progress.coerceIn(0f, 1f) * containerWidth * BACK_EDGE_SHIFT_FRACTION
    }

    /**
     * The size a photo of [mediaWidth] by [mediaHeight] has when it is shown whole, centred, in a container of
     * [containerWidth] by [containerHeight] (the viewer's pages show it contained). The whole container when the
     * photo's size is not known.
     */
    fun fitSize(containerWidth: Float, containerHeight: Float, mediaWidth: Int, mediaHeight: Int): PhotoSize {
        if (mediaWidth <= 0 || mediaHeight <= 0 || containerWidth <= 0f || containerHeight <= 0f) {
            return PhotoSize(containerWidth, containerHeight)
        }
        val fit = minOf(containerWidth / mediaWidth, containerHeight / mediaHeight)
        return PhotoSize(mediaWidth * fit, mediaHeight * fit)
    }

    /**
     * Where the photo is, in the container's coordinates, when the container's content is shown at [scale] about its
     * centre and moved by ([offsetX], [offsetY]): the photo ([photo], shown whole and centred at rest) scaled and moved
     * the same way. This is `dragTransform`'s placement.
     */
    fun photoRectFor(containerWidth: Float, containerHeight: Float, photo: PhotoSize, scale: Float, offsetX: Float, offsetY: Float): PhotoRect {
        val centerX = containerWidth / 2f + offsetX
        val centerY = containerHeight / 2f + offsetY
        val halfWidth = photo.width * scale / 2f
        val halfHeight = photo.height * scale / 2f
        return PhotoRect(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
    }

    /**
     * The state the viewer's content ends in when the photo has flown into its grid [cell] (in the container's
     * coordinates): centred on the cell and as small as it can be while still covering it, like the cell's own picture,
     * which is cropped to the cell. The rest of the page is cut off by the clip.
     */
    fun flightEnd(containerWidth: Float, containerHeight: Float, photo: PhotoSize, cell: PhotoRect): FlightEnd {
        val scale = max(cell.width / photo.width, cell.height / photo.height)
        return FlightEnd(
            scale = scale,
            offsetX = cell.centerX - containerWidth / 2f,
            offsetY = cell.centerY - containerHeight / 2f,
        )
    }

    fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

    fun lerp(from: PhotoRect, to: PhotoRect, fraction: Float): PhotoRect = PhotoRect(
        lerp(from.left, to.left, fraction),
        lerp(from.top, to.top, fraction),
        lerp(from.right, to.right, fraction),
        lerp(from.bottom, to.bottom, fraction),
    )

    /** A flick at this speed (pixels per second) or faster gets the shortest closing flight. */
    const val CLOSE_FASTEST_SPEED = 5000f

    /** The fastest closing flight lasts this fraction of the normal one. */
    const val CLOSE_FASTEST_FRACTION = 0.67f

    /**
     * How long the photo takes to fly back to its cell: [baseMillis] for a gentle release or the Back button, down to
     * about two thirds of it for a hard flick, so a fast throw does not linger.
     */
    fun closeMillis(speed: Float, baseMillis: Int): Int {
        val fraction = (abs(speed) / CLOSE_FASTEST_SPEED).coerceIn(0f, 1f)
        return (baseMillis * (1f - (1f - CLOSE_FASTEST_FRACTION) * fraction)).roundToInt()
    }

    /** Pulling down by this fraction of the screen height takes progress from 0 to 1. */
    const val FULL_DRAG_FRACTION = 0.4f

    /** Letting go past this progress closes the viewer. */
    const val DISMISS_AT_PROGRESS = 0.3f

    /** A downward flick faster than this (pixels per second) closes the viewer from any distance. */
    const val DISMISS_FLING_SPEED = 1600f

    /** The photo can only be pulled up by this fraction of the height, with growing resistance. */
    const val MAX_UP_FRACTION = 0.12f

    /** Letting go with the photo pulled up this far (fraction of the height) opens the info sheet. */
    const val INFO_AT_FRACTION = 0.06f

    /** An upward flick faster than this opens the info sheet from any distance. */
    const val INFO_FLING_SPEED = 1200f

    /** How small the photo gets when it has been pulled all the way. */
    const val MIN_SCALE = 0.65f

    enum class Release { Dismiss, ShowInfo, Restore }

    /** 0 while the photo is at rest or pulled up, up to 1 when it has been pulled all the way down. */
    fun progress(offsetY: Float, height: Float): Float =
        if (height <= 0f) 0f else (offsetY / (height * FULL_DRAG_FRACTION)).coerceIn(0f, 1f)

    fun scale(progress: Float): Float = 1f - (1f - MIN_SCALE) * progress.coerceIn(0f, 1f)

    /** The background fades out completely by the time the photo is pulled all the way. */
    fun backgroundAlpha(progress: Float): Float = 1f - progress.coerceIn(0f, 1f)

    /** The bars are gone quickly, after a fifth of the way. */
    fun chromeAlpha(progress: Float): Float = (1f - 5f * progress).coerceIn(0f, 1f)

    /** Turns how far the finger has gone into how far the photo moves: down is 1:1, up resists. */
    fun resistUp(rawOffsetY: Float, height: Float): Float {
        if (rawOffsetY >= 0f) return rawOffsetY
        val limit = height * MAX_UP_FRACTION
        if (limit <= 0f) return 0f
        return -limit * (1f - exp(rawOffsetY / limit))
    }

    /** What to do when the finger lifts with the photo [offsetY] away from rest, moving at [velocityY]. */
    fun release(offsetY: Float, velocityY: Float, height: Float): Release = when {
        progress(offsetY, height) >= DISMISS_AT_PROGRESS ||
            (velocityY >= DISMISS_FLING_SPEED && offsetY > 0f) -> Release.Dismiss
        offsetY <= -height * INFO_AT_FRACTION ||
            (velocityY <= -INFO_FLING_SPEED && offsetY < 0f) -> Release.ShowInfo
        else -> Release.Restore
    }
}
