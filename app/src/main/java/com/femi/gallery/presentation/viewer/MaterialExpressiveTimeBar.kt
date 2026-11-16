package com.femi.gallery.presentation.viewer

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.TimeBar
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.max

/**
 * A Material 3 Expressive time bar that smoothly expands into a bold pill track when scrubbing.
 * Features:
 * - Idle 6dp sleek pill track; expands smoothly to 16dp expressive track while scrubbing.
 * - Physics-based overshoot spring easing for a delightful, responsive feel.
 * - Vertical pill capsule thumb with glowing touch halo.
 * - Floating time preview badge that follows the finger in real time during scrubbing.
 * - Ample vertical headroom so the floating badge is never clipped by view bounds or video canvas.
 * - Tactile clock-tick haptic feedback.
 */
@OptIn(UnstableApi::class)
class MaterialExpressiveTimeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr), TimeBar {

    private val density = context.resources.displayMetrics.density

    // Proportions
    private val normalBarHeight = 6f * density
    private val expandedBarHeight = 16f * density
    private val normalThumbRadius = 7f * density
    private val expandedThumbRadius = 10f * density
    private val haloExtraRadius = 8f * density

    // Paints
    private val unplayedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF.toInt() // Soft translucent track
    }
    private val bufferedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF.toInt() // Frosted buffered segment
    }
    private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt() // Crisp white played progress
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFFFFF.toInt() // Touch glow
    }

    // Floating scrub badge paints
    private val badgeBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xF21C1B1F.toInt() // High-contrast dark elevated surface
    }
    private val badgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x4DFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 13f * density
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    // Playback state
    private var positionMs = 0L
    private var bufferedPositionMs = 0L
    private var durationMs = 0L
    private var isScrubbing = false
    private var scrubPositionMs = 0L

    // Expansion animation state (0f = normal, 1f = expanded)
    private var expansionFraction = 0f
    private var expansionAnimator: ValueAnimator? = null

    // TimeBar listeners
    private val listeners = CopyOnWriteArraySet<TimeBar.OnScrubListener>()

    // Reusable drawing rects
    private val trackRect = RectF()
    private val badgeRect = RectF()

    override fun addListener(listener: TimeBar.OnScrubListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: TimeBar.OnScrubListener) {
        listeners.remove(listener)
    }

    override fun setKeyTimeIncrement(time: Long) {}

    override fun setKeyCountIncrement(count: Int) {}

    override fun setPosition(position: Long) {
        if (this.positionMs == position) return
        this.positionMs = position
        if (!isScrubbing) {
            invalidate()
        }
    }

    override fun setBufferedPosition(bufferedPosition: Long) {
        if (this.bufferedPositionMs == bufferedPosition) return
        this.bufferedPositionMs = bufferedPosition
        invalidate()
    }

    override fun setDuration(duration: Long) {
        if (this.durationMs == duration) return
        this.durationMs = duration
        invalidate()
    }

    override fun getPreferredUpdateDelay(): Long = 16L

    override fun setAdGroupTimesMs(
        adGroupTimesMs: LongArray?,
        playedAdGroups: BooleanArray?,
        adGroupCount: Int,
    ) {}

    private fun setScrubbing(scrubbing: Boolean) {
        if (isScrubbing == scrubbing) return
        isScrubbing = scrubbing
        expansionAnimator?.cancel()
        val target = if (scrubbing) 1f else 0f
        expansionAnimator = ValueAnimator.ofFloat(expansionFraction, target).apply {
            duration = if (scrubbing) 200L else 220L
            interpolator = if (scrubbing) OvershootInterpolator(1.1f) else OvershootInterpolator(0.8f)
            addUpdateListener { anim ->
                expansionFraction = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun xToPosition(x: Float): Long {
        val usableLeft = paddingLeft.toFloat()
        val usableRight = (width - paddingRight).toFloat()
        val usableWidth = usableRight - usableLeft
        if ((usableWidth <= 0f) || (durationMs <= 0L)) return 0L
        val clampedX = x.coerceIn(usableLeft, usableRight)
        val fraction = (clampedX - usableLeft) / usableWidth
        return (fraction * durationMs).toLong()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || durationMs <= 0L) {
            return super.onTouchEvent(event)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                setScrubbing(true)
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                scrubPositionMs = xToPosition(event.x)
                listeners.forEach { it.onScrubStart(this, scrubPositionMs) }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isScrubbing) {
                    scrubPositionMs = xToPosition(event.x)
                    listeners.forEach { it.onScrubMove(this, scrubPositionMs) }
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isScrubbing) {
                    val canceled = event.action == MotionEvent.ACTION_CANCEL
                    parent?.requestDisallowInterceptTouchEvent(false)
                    setScrubbing(false)
                    listeners.forEach { it.onScrubStop(this, scrubPositionMs, canceled) }
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val usableLeft = paddingLeft.toFloat()
        val usableRight = (width - paddingRight).toFloat()
        val usableWidth = usableRight - usableLeft
        if (usableWidth <= 0f) return

        // Place track comfortably at bottom of view so floating badge fits above it
        val centerY = height - (16f * density)
        val currentBarHeight = normalBarHeight + (expandedBarHeight - normalBarHeight) * expansionFraction
        val top = centerY - currentBarHeight / 2f
        val bottom = centerY + currentBarHeight / 2f
        val pillRadius = currentBarHeight / 2f

        // 1. Unplayed background track (full pill)
        trackRect.set(usableLeft, top, usableRight, bottom)
        canvas.drawRoundRect(trackRect, pillRadius, pillRadius, unplayedPaint)

        val activePos = if (isScrubbing) scrubPositionMs else positionMs
        val duration = max(durationMs, 1L)

        // 2. Buffered progress
        if (bufferedPositionMs > 0L) {
            val bufferedFrac = (bufferedPositionMs.toFloat() / duration).coerceIn(0f, 1f)
            val bufferedRight = usableLeft + usableWidth * bufferedFrac
            if (bufferedRight > usableLeft) {
                trackRect.set(usableLeft, top, bufferedRight, bottom)
                canvas.drawRoundRect(trackRect, pillRadius, pillRadius, bufferedPaint)
            }
        }

        // 3. Played progress
        val playedFrac = (activePos.toFloat() / duration).coerceIn(0f, 1f)
        val playedRight = usableLeft + usableWidth * playedFrac
        if (playedRight > usableLeft) {
            trackRect.set(usableLeft, top, playedRight, bottom)
            canvas.drawRoundRect(trackRect, pillRadius, pillRadius, playedPaint)
        }

        // 4. Scrubber Thumb with glowing halo
        val thumbRadius = normalThumbRadius + (expandedThumbRadius - normalThumbRadius) * expansionFraction
        if (expansionFraction > 0.05f) {
            val haloRadius = thumbRadius + haloExtraRadius * expansionFraction
            canvas.drawCircle(playedRight, centerY, haloRadius, haloPaint)
        }
        canvas.drawCircle(playedRight, centerY, thumbRadius, thumbPaint)

        // 5. Floating scrub preview badge directly above the thumb within view bounds
        if (expansionFraction > 0.05f) {
            val badgeAlpha = ((expansionFraction.coerceIn(0f, 1f)) * 255).toInt()
            badgeBackgroundPaint.alpha = (badgeAlpha * 0.96f).toInt()
            badgeStrokePaint.alpha = (badgeAlpha * 0.4f).toInt()
            badgeTextPaint.alpha = badgeAlpha

            val timeText = formatTime(activePos)
            val textWidth = badgeTextPaint.measureText(timeText)
            val badgeWidth = textWidth + (22f * density)
            val badgeHeight = 24f * density
            val badgeRadius = badgeHeight / 2f

            val floatGap = (10f * density) * expansionFraction
            val badgeCenterY = top - floatGap - badgeHeight / 2f
            val badgeCenterX = playedRight.coerceIn(usableLeft + badgeWidth / 2f, usableRight - badgeWidth / 2f)

            badgeRect.set(
                badgeCenterX - badgeWidth / 2f,
                badgeCenterY - badgeHeight / 2f,
                badgeCenterX + badgeWidth / 2f,
                badgeCenterY + badgeHeight / 2f,
            )
            canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgeBackgroundPaint)
            canvas.drawRoundRect(badgeRect, badgeRadius, badgeRadius, badgeStrokePaint)

            val textBaseline = badgeCenterY - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
            canvas.drawText(timeText, badgeCenterX, textBaseline, badgeTextPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minHeight = (64f * density).toInt()
        val height = resolveSizeAndState(minHeight, heightMeasureSpec, 0)
        val width = getDefaultSize(suggestedMinimumWidth, widthMeasureSpec)
        setMeasuredDimension(width, height)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hours = minutes / 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}
