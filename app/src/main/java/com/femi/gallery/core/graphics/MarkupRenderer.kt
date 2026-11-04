package com.femi.gallery.core.graphics

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.femi.gallery.domain.edit.MarkupItem
import com.femi.gallery.domain.edit.MarkupMath
import com.femi.gallery.domain.edit.MarkupSticker
import com.femi.gallery.domain.edit.MarkupStroke
import com.femi.gallery.domain.edit.MarkupText
import kotlin.math.min

/**
 * Draws the drawings, text and stickers of an edit onto a canvas of [width] by [height] pixels. The editor's preview
 * and the saved photo both go through here, so what is saved looks like what was on the screen, whatever the size.
 */
object MarkupRenderer {
    private const val LINE_HEIGHT = 1.2f
    private const val STICKER_TEXT_SCALE = 0.8f
    private const val HIGHLIGHTER_ALPHA = 0x66

    fun draw(canvas: Canvas, width: Float, height: Float, items: List<MarkupItem>, selectedId: Long? = null) {
        val shorter = min(width, height)
        for (item in items) {
            when (item) {
                is MarkupStroke -> drawStroke(canvas, item, width, height, shorter)
                is MarkupText -> drawText(canvas, item, width, height, shorter)
                is MarkupSticker -> drawSticker(canvas, item, width, height, shorter)
            }
        }
        if (selectedId != null) items.firstOrNull { it.id == selectedId }?.let { drawSelection(canvas, it, width, height, shorter) }
    }

    private fun drawStroke(canvas: Canvas, stroke: MarkupStroke, width: Float, height: Float, shorter: Float) {
        if (stroke.points.isEmpty()) return
        val strokeWidth = stroke.widthFraction * shorter * (if (stroke.highlighter) MarkupMath.HIGHLIGHTER_WIDTH_FACTOR else 1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.colorArgb
            if (stroke.highlighter) alpha = HIGHLIGHTER_ALPHA
            this.strokeWidth = strokeWidth
            strokeJoin = Paint.Join.ROUND
            strokeCap = if (stroke.highlighter) Paint.Cap.SQUARE else Paint.Cap.ROUND
        }
        val first = stroke.points.first()
        if (stroke.points.size == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(first.x * width, first.y * height, strokeWidth / 2f, paint)
            return
        }
        paint.style = Paint.Style.STROKE
        val path = Path()
        path.moveTo(first.x * width, first.y * height)
        for (i in 1 until stroke.points.size - 1) {
            val p = stroke.points[i]
            val next = stroke.points[i + 1]
            // Curves through the midpoints, so that the line is smooth and not made of straight pieces.
            path.quadTo(p.x * width, p.y * height, (p.x + next.x) / 2f * width, (p.y + next.y) / 2f * height)
        }
        val last = stroke.points.last()
        path.lineTo(last.x * width, last.y * height)
        canvas.drawPath(path, paint)
    }

    private fun drawText(canvas: Canvas, item: MarkupText, width: Float, height: Float, shorter: Float) {
        val size = item.sizeFraction * shorter
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val lines = item.text.split('\n')
        val lineHeight = size * LINE_HEIGHT
        val block = lineHeight * lines.size
        val metrics = paint.fontMetrics
        val outline = if (luminance(item.colorArgb) > 140) 0xAA000000.toInt() else 0xAAFFFFFF.toInt()
        canvas.save()
        canvas.translate(item.center.x * width, item.center.y * height)
        canvas.rotate(item.rotationDegrees)
        lines.forEachIndexed { index, line ->
            val baseline = -block / 2f + index * lineHeight + lineHeight / 2f - (metrics.ascent + metrics.descent) / 2f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = size * 0.12f
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = outline
            canvas.drawText(line, 0f, baseline, paint)
            paint.style = Paint.Style.FILL
            paint.color = item.colorArgb
            canvas.drawText(line, 0f, baseline, paint)
        }
        canvas.restore()
    }

    private fun drawSticker(canvas: Canvas, item: MarkupSticker, width: Float, height: Float, shorter: Float) {
        val size = item.sizeFraction * shorter
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * STICKER_TEXT_SCALE
            textAlign = Paint.Align.CENTER
        }
        val metrics = paint.fontMetrics
        canvas.save()
        canvas.translate(item.center.x * width, item.center.y * height)
        canvas.rotate(item.rotationDegrees)
        canvas.drawText(item.emoji, 0f, -(metrics.ascent + metrics.descent) / 2f, paint)
        canvas.restore()
    }

    /** A dashed frame around the selected item (only in the editor's preview). */
    private fun drawSelection(canvas: Canvas, item: MarkupItem, width: Float, height: Float, shorter: Float) {
        val (center, rotation) = when (item) {
            is MarkupText -> item.center to item.rotationDegrees
            is MarkupSticker -> item.center to item.rotationDegrees
            is MarkupStroke -> return
        }
        val (w, h) = MarkupMath.boxSize(item, shorter)
        val pad = shorter * 0.015f
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = shorter * 0.004f + 1f
            color = 0xFFFFFFFF.toInt()
            pathEffect = DashPathEffect(floatArrayOf(shorter * 0.02f, shorter * 0.015f), 0f)
            setShadowLayer(shorter * 0.004f, 0f, 0f, 0xFF000000.toInt())
        }
        canvas.save()
        canvas.translate(center.x * width, center.y * height)
        canvas.rotate(rotation)
        canvas.drawRect(-w / 2f - pad, -h / 2f - pad, w / 2f + pad, h / 2f + pad, frame)
        canvas.restore()
    }

    private fun luminance(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }
}
