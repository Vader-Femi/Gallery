package com.femi.gallery.domain.edit

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The rules of drawing on a photo, without anything of Android, so that they can be tested. */
object MarkupMath {
    const val MIN_SIZE = 0.02f
    const val MAX_SIZE = 1.2f
    const val DEFAULT_PEN_WIDTH = 0.012f
    const val HIGHLIGHTER_WIDTH_FACTOR = 3.5f
    const val DEFAULT_TEXT_SIZE = 0.07f
    const val DEFAULT_STICKER_SIZE = 0.16f

    /** Rough width of a character as a fraction of the text height; enough to find where a piece of text is. */
    private const val CHARACTER_WIDTH = 0.6f
    private const val LINE_HEIGHT = 1.2f

    /**
     * Removes points that are within [minDistance] (a fraction of the shorter side) of the last kept point, so that a
     * slow stroke does not make thousands of points; the first and last points are always kept.
     */
    fun simplify(points: List<MarkupPoint>, minDistance: Float, imageWidth: Float, imageHeight: Float): List<MarkupPoint> {
        if (points.size <= 2) return points
        val shorter = min(imageWidth, imageHeight)
        val kept = ArrayList<MarkupPoint>()
        kept.add(points.first())
        for (i in 1 until points.size - 1) {
            val last = kept.last()
            val distance = hypot((points[i].x - last.x) * imageWidth, (points[i].y - last.y) * imageHeight) / shorter
            if (distance >= minDistance) kept.add(points[i])
        }
        kept.add(points.last())
        return kept
    }

    /**
     * Moves an item by [dx], [dy] (fractions of the photo's width and height), scales it by [zoom] and turns it by
     * [rotationDegrees]. The centre stays on the photo and the size stays within sensible limits. Strokes cannot be
     * moved this way and are returned as they are.
     */
    fun transform(item: MarkupItem, dx: Float, dy: Float, zoom: Float, rotationDegrees: Float): MarkupItem = when (item) {
        is MarkupText -> item.copy(
            center = moved(item.center, dx, dy),
            sizeFraction = (item.sizeFraction * zoom).coerceIn(MIN_SIZE, MAX_SIZE),
            rotationDegrees = normalizeDegrees(item.rotationDegrees + rotationDegrees),
        )
        is MarkupSticker -> item.copy(
            center = moved(item.center, dx, dy),
            sizeFraction = (item.sizeFraction * zoom).coerceIn(MIN_SIZE, MAX_SIZE),
            rotationDegrees = normalizeDegrees(item.rotationDegrees + rotationDegrees),
        )
        is MarkupStroke -> item
    }

    private fun moved(p: MarkupPoint, dx: Float, dy: Float) = MarkupPoint((p.x + dx).coerceIn(0f, 1f), (p.y + dy).coerceIn(0f, 1f))

    /** Degrees in (-180, 180]. */
    fun normalizeDegrees(degrees: Float): Float {
        var d = degrees % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    /** The size of an item in pixels, (width, height), before it is turned. */
    fun boxSize(item: MarkupItem, shorterSide: Float): Pair<Float, Float> = when (item) {
        is MarkupText -> {
            val lines = item.text.split('\n')
            val height = item.sizeFraction * shorterSide
            val widest = lines.maxOf { it.codePointCount(0, it.length) }
            max(widest, 1) * height * CHARACTER_WIDTH to lines.size * height * LINE_HEIGHT
        }
        is MarkupSticker -> (item.sizeFraction * shorterSide).let { it to it }
        is MarkupStroke -> 0f to 0f
    }

    /**
     * The id of the item (text or sticker; the one drawn last wins) under the point ([x], [y]) given as fractions of
     * the photo, on a photo of [imageWidth] by [imageHeight] pixels; null when there is none. [slop] (a fraction of the
     * shorter side) makes small items easier to hit.
     */
    fun itemAt(items: List<MarkupItem>, x: Float, y: Float, imageWidth: Float, imageHeight: Float, slop: Float = 0.03f): Long? {
        val shorter = min(imageWidth, imageHeight)
        for (item in items.asReversed()) {
            val (center, rotation) = when (item) {
                is MarkupText -> item.center to item.rotationDegrees
                is MarkupSticker -> item.center to item.rotationDegrees
                is MarkupStroke -> continue
            }
            val (w, h) = boxSize(item, shorter)
            // The touch point in the item's own (unturned) frame, in pixels from its centre.
            val px = (x - center.x) * imageWidth
            val py = (y - center.y) * imageHeight
            val a = Math.toRadians(-rotation.toDouble())
            val localX = px * cos(a) - py * sin(a)
            val localY = px * sin(a) + py * cos(a)
            if (abs(localX) <= w / 2 + slop * shorter && abs(localY) <= h / 2 + slop * shorter) return item.id
        }
        return null
    }

    /** A new id that no item in [items] has. */
    fun nextId(items: List<MarkupItem>): Long = (items.maxOfOrNull { it.id } ?: 0L) + 1L
}
