package com.femi.gallery.presentation.viewer

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The numbers behind dragging a photo away: how far it has been pulled, how small it gets, how the
 * background and the bars fade, and what letting go means. Pure, so it can be tested.
 */
/** A rectangle in pixels, as the photo's page is placed on screen. */
internal data class PhotoRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

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
