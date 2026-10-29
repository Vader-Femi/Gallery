package com.femi.gallery.domain.scan

import com.femi.gallery.domain.scan.ScanWaitPlan.Step
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanWaitPlanTest {
    private val limit = 480_000L

    @Test
    fun finishedScansEndTheRun() {
        assertEquals(Step.Done, ScanWaitPlan.next(anyScanRunning = false, elapsedMs = 5_000, limitMs = limit))
    }

    @Test
    fun finishedEvenWhenTimeIsUp() {
        assertEquals(Step.Done, ScanWaitPlan.next(anyScanRunning = false, elapsedMs = limit + 1, limitMs = limit))
    }

    @Test
    fun runningScansAreWaitedForWhileThereIsTime() {
        assertEquals(Step.Wait, ScanWaitPlan.next(anyScanRunning = true, elapsedMs = 0, limitMs = limit))
        assertEquals(Step.Wait, ScanWaitPlan.next(anyScanRunning = true, elapsedMs = limit - 1, limitMs = limit))
    }

    @Test
    fun runningScansAreGivenUpOnWhenTimeIsUp() {
        assertEquals(Step.GiveUp, ScanWaitPlan.next(anyScanRunning = true, elapsedMs = limit, limitMs = limit))
        assertEquals(Step.GiveUp, ScanWaitPlan.next(anyScanRunning = true, elapsedMs = limit * 2, limitMs = limit))
    }
}
