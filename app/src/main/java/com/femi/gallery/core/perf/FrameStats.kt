package com.femi.gallery.core.perf

import java.util.Locale

/** What went wrong in a window of frames, ready to be logged. */
class FrameReport(
    val frames: Int,
    /** Frames that took longer than their deadline. */
    val janky: Int,
    /** Frames that took more than [FrameStats.FROZEN_NANOS]: the user sees the app stop. */
    val frozen: Int,
    val medianMillis: Float,
    val p95Millis: Float,
    val worstMillis: Float,
    /** The slowest phases of the slowest frame, largest first, in milliseconds. */
    val worstPhases: List<Pair<String, Float>>,
    val budgetMillis: Float,
    val windowMillis: Long,
) {
    fun format(screen: String): String {
        val percent = if (frames == 0) 0 else janky * 100 / frames
        val phases = worstPhases.joinToString(", ") { (name, ms) -> String.format(Locale.ROOT, "%s %.1f", name, ms) }
        return String.format(
            Locale.ROOT,
            "[%s] %.1f s: %d frames, %d janky (%d%%), %d frozen, median %.1f ms, p95 %.1f ms, " +
                "worst %.1f ms (%s) budget %.1f ms",
            screen.ifEmpty { "?" }, windowMillis / 1000.0, frames, janky, percent, frozen,
            medianMillis, p95Millis, worstMillis, phases, budgetMillis,
        )
    }
}

/**
 * Collects the duration of every frame and, every [intervalMillis], reports how the window went.
 * A window without a single late frame produces no report, so the log only shows problems.
 *
 * Not thread-safe: feed it from one thread.
 */
class FrameStats(private val intervalMillis: Long = REPORT_INTERVAL_MILLIS) {

    private var totals = LongArray(512)
    private var count = 0
    private var janky = 0
    private var frozen = 0
    private var windowStart = -1L
    private var budgetNanos = 0L
    private var worstTotal = -1L
    private var worstPhases = LongArray(PHASES.size)

    /**
     * Records one frame: it took [totalNanos] against a deadline of [deadlineNanos], of which
     * [phaseNanos] (indexed like [PHASES]) were spent in each phase. [nowMillis] is any steady clock.
     * Returns a report when the window has ended and it contained late frames.
     */
    fun add(totalNanos: Long, deadlineNanos: Long, phaseNanos: LongArray, nowMillis: Long): FrameReport? {
        if (windowStart < 0) windowStart = nowMillis
        if (count == totals.size) totals = totals.copyOf(count * 2)
        totals[count++] = totalNanos
        budgetNanos = if (deadlineNanos > 0) deadlineNanos else DEFAULT_BUDGET_NANOS
        if (totalNanos > budgetNanos) janky++
        if (totalNanos > FROZEN_NANOS) frozen++
        if (totalNanos > worstTotal) {
            worstTotal = totalNanos
            phaseNanos.copyInto(worstPhases, endIndex = minOf(phaseNanos.size, worstPhases.size))
        }
        return if (nowMillis - windowStart >= intervalMillis) flush(nowMillis) else null
    }

    /** Ends the window now (for example when the app goes to the background). */
    fun flush(nowMillis: Long): FrameReport? {
        val report = if (count > 0 && janky > 0) buildReport(nowMillis - windowStart) else null
        count = 0
        janky = 0
        frozen = 0
        worstTotal = -1L
        worstPhases.fill(0L)
        windowStart = -1L
        return report
    }

    private fun buildReport(windowMillis: Long): FrameReport {
        val sorted = totals.copyOf(count).also { it.sort() }
        val phases = PHASES.indices
            .map { PHASES[it] to worstPhases[it] / NANOS_PER_MILLI }
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
            .take(3)
        return FrameReport(
            frames = count,
            janky = janky,
            frozen = frozen,
            medianMillis = sorted[(count - 1) / 2] / NANOS_PER_MILLI,
            p95Millis = sorted[((count - 1) * 95) / 100] / NANOS_PER_MILLI,
            worstMillis = worstTotal / NANOS_PER_MILLI,
            worstPhases = phases,
            budgetMillis = budgetNanos / NANOS_PER_MILLI,
            windowMillis = windowMillis,
        )
    }

    companion object {
        /** The phases of a frame, in the order the caller passes their durations. */
        val PHASES = arrayOf("input", "animation", "layout", "draw", "sync", "commands", "swap", "gpu")
        const val INPUT = 0
        const val ANIMATION = 1
        const val LAYOUT = 2
        const val DRAW = 3
        const val SYNC = 4
        const val COMMANDS = 5
        const val SWAP = 6
        const val GPU = 7

        const val REPORT_INTERVAL_MILLIS = 3_000L
        const val FROZEN_NANOS = 100_000_000L
        private const val DEFAULT_BUDGET_NANOS = 16_666_667L
        private const val NANOS_PER_MILLI = 1_000_000f
    }
}
