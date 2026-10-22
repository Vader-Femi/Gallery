package com.femi.gallery.core.perf

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window

/** The screen the user is on, so that a slow frame can be blamed on it. Set by the navigation. */
object ScreenTag {
    /** The screen the navigation is on. */
    @Volatile
    var base: String = ""

    /** What is showing over it, such as the photo viewer, if anything. */
    @Volatile
    var overlay: String? = null

    val name: String get() = overlay ?: base
}

/**
 * Watches how long the window's frames take and logs, under the tag "Frames", every few seconds in
 * which any frame was late: which screen, how many frames, and where the slowest one spent its time
 * (layout, draw, GPU...). Costs next to nothing, and is silent while the app runs smoothly.
 */
class FrameMonitor(private val window: Window) {

    private var thread: HandlerThread? = null
    private val stats = FrameStats()
    private val phases = LongArray(FrameStats.PHASES.size)

    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        // The first frame of a window includes inflating everything; it says nothing about smoothness.
        if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
        phases[FrameStats.INPUT] = metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION)
        phases[FrameStats.ANIMATION] = metrics.getMetric(FrameMetrics.ANIMATION_DURATION)
        phases[FrameStats.LAYOUT] = metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
        phases[FrameStats.DRAW] = metrics.getMetric(FrameMetrics.DRAW_DURATION)
        phases[FrameStats.SYNC] = metrics.getMetric(FrameMetrics.SYNC_DURATION)
        phases[FrameStats.COMMANDS] = metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)
        phases[FrameStats.SWAP] = metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)
        phases[FrameStats.GPU] = metrics.getMetric(FrameMetrics.GPU_DURATION)
        phases[FrameStats.DELAY] = metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION)
        stats.add(
            totalNanos = metrics.getMetric(FrameMetrics.TOTAL_DURATION),
            deadlineNanos = metrics.getMetric(FrameMetrics.DEADLINE),
            phaseNanos = phases,
            nowMillis = SystemClock.elapsedRealtime(),
        )?.let { Log.i(TAG, it.format(ScreenTag.name)) }
    }

    fun start() {
        if (thread != null) return
        val worker = HandlerThread("FrameMonitor").also { it.start() }
        thread = worker
        window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper))
    }

    fun stop() {
        val worker = thread ?: return
        window.removeOnFrameMetricsAvailableListener(listener)
        worker.quitSafely()
        thread = null
        stats.flush(SystemClock.elapsedRealtime())?.let { Log.i(TAG, it.format(ScreenTag.name)) }
    }

    private companion object {
        const val TAG = "Frames"
    }
}
