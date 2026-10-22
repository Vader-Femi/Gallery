package com.femi.gallery.core.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameStatsTest {

    private val ms = 1_000_000L
    private val budget = 11 * ms // a 90 Hz screen
    private val noPhases = LongArray(FrameStats.PHASES.size)

    private fun FrameStats.frame(totalMs: Long, at: Long, phases: LongArray = noPhases) =
        add(totalMs * ms, budget, phases, at)

    @Test
    fun aSmoothWindowReportsNothing() {
        val stats = FrameStats(intervalMillis = 1_000)
        for (i in 0 until 100) assertNull(stats.frame(8, at = i * 11L))
        assertNull(stats.frame(8, at = 1_200)) // the window ends here, with no late frame in it
    }

    @Test
    fun aWindowWithLateFramesIsReportedWhenItEnds() {
        val stats = FrameStats(intervalMillis = 1_000)
        for (i in 0 until 90) assertNull(stats.frame(if (i % 10 == 0) 25 else 8, at = i * 11L))
        val report = stats.frame(8, at = 1_000)!!
        assertEquals(91, report.frames)
        assertEquals(9, report.janky)
        assertEquals(0, report.frozen)
        assertEquals(8f, report.medianMillis, 0.01f)
        assertEquals(25f, report.worstMillis, 0.01f)
        assertEquals(11f, report.budgetMillis, 0.01f)
    }

    @Test
    fun theWorstFramesBiggestPhasesAreNamedLargestFirst() {
        val stats = FrameStats(intervalMillis = 1_000)
        val slow = LongArray(FrameStats.PHASES.size).also {
            it[FrameStats.LAYOUT] = 30 * ms
            it[FrameStats.DRAW] = 9 * ms
            it[FrameStats.GPU] = 5 * ms
            it[FrameStats.SYNC] = 1 * ms
        }
        stats.frame(8, at = 0)
        stats.frame(50, at = 100, phases = slow)
        stats.frame(20, at = 200, phases = LongArray(FrameStats.PHASES.size).also { it[FrameStats.DRAW] = 19 * ms })
        val report = stats.frame(8, at = 1_000)!!
        assertEquals(listOf("layout", "draw", "gpu"), report.worstPhases.map { it.first })
        assertEquals(30f, report.worstPhases.first().second, 0.01f)
    }

    @Test
    fun frozenFramesAreCountedSeparately() {
        val stats = FrameStats(intervalMillis = 1_000)
        stats.frame(8, at = 0)
        stats.frame(150, at = 100)
        stats.frame(40, at = 300)
        val report = stats.frame(8, at = 1_000)!!
        assertEquals(2, report.janky)
        assertEquals(1, report.frozen)
    }

    @Test
    fun theNextWindowStartsFresh() {
        val stats = FrameStats(intervalMillis = 1_000)
        stats.frame(40, at = 0)
        assertNotNull(stats.frame(8, at = 1_000))
        assertNull(stats.frame(8, at = 1_500))
        assertNull(stats.frame(8, at = 2_100)) // smooth window: nothing
    }

    @Test
    fun flushEndsTheWindowEarly() {
        val stats = FrameStats(intervalMillis = 60_000)
        stats.frame(30, at = 0)
        stats.frame(8, at = 10)
        val report = stats.flush(nowMillis = 500)!!
        assertEquals(2, report.frames)
        assertEquals(500L, report.windowMillis)
        assertNull(stats.flush(nowMillis = 600)) // nothing left
    }

    @Test
    fun aMissingDeadlineFallsBackToSixtyHertz() {
        val stats = FrameStats(intervalMillis = 1_000)
        stats.add(15 * ms, 0, noPhases, 0) // fine at 60 Hz
        stats.add(20 * ms, 0, noPhases, 10) // late at 60 Hz
        val report = stats.flush(100)!!
        assertEquals(1, report.janky)
        assertEquals(16.67f, report.budgetMillis, 0.01f)
    }

    @Test
    fun percentilesComeFromTheSortedDurations() {
        val stats = FrameStats(intervalMillis = 100_000)
        for (i in 1..100) stats.frame(i.toLong(), at = i.toLong())
        val report = stats.flush(200)!!
        assertEquals(100, report.frames)
        assertEquals(50f, report.medianMillis, 1f)
        assertEquals(95f, report.p95Millis, 1f)
        assertEquals(100f, report.worstMillis, 0.01f)
    }

    @Test
    fun theReportIsOneReadableLine() {
        val stats = FrameStats(intervalMillis = 1_000)
        val phases = LongArray(FrameStats.PHASES.size).also { it[FrameStats.LAYOUT] = 30 * ms }
        stats.frame(8, at = 0)
        stats.frame(45, at = 100, phases = phases)
        val line = stats.frame(8, at = 1_000)!!.format("pictures")
        assertTrue(line, line.startsWith("[pictures] 1.0 s: 3 frames, 1 janky (33%)"))
        assertTrue(line, line.contains("worst 45.0 ms (layout 30.0)"))
        assertTrue(stats.frame(40, at = 1_100).let { true })
        assertTrue(stats.flush(2_000)!!.format("").startsWith("[?]"))
    }

    @Test
    fun manyFramesDoNotOverflowTheBuffer() {
        val stats = FrameStats(intervalMillis = 1_000_000)
        for (i in 0 until 5_000) stats.frame(if (i == 0) 30 else 5, at = i.toLong())
        assertEquals(5_000, stats.flush(6_000)!!.frames)
    }

    @Test
    fun timeSpentWaitingForTheMainThreadIsNamedLikeAPhase() {
        val stats = FrameStats(intervalMillis = 1_000)
        val stalled = LongArray(FrameStats.PHASES.size).also {
            it[FrameStats.DELAY] = 400 * ms
            it[FrameStats.ANIMATION] = 50 * ms
        }
        stats.frame(8, at = 0)
        stats.frame(480, at = 100, phases = stalled)
        val report = stats.frame(8, at = 1_000)!!
        assertEquals("delay", report.worstPhases.first().first)
        assertEquals(400f, report.worstPhases.first().second, 0.01f)
        assertEquals(1, report.frozen)
    }
}
