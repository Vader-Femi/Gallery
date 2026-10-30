package com.femi.gallery.domain.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressThrottleTest {

    @Test
    fun theFirstProgressIsShown() {
        assertTrue(ProgressThrottle(3_000).shouldUpdate(0, 100, nowMs = 10_000))
    }

    @Test
    fun aQuickChangeWaits() {
        val throttle = ProgressThrottle(3_000)
        throttle.shouldUpdate(0, 100, 10_000)
        assertFalse(throttle.shouldUpdate(5, 100, 11_000))
    }

    @Test
    fun aChangeAfterTheIntervalIsShown() {
        val throttle = ProgressThrottle(3_000)
        throttle.shouldUpdate(0, 100, 10_000)
        assertTrue(throttle.shouldUpdate(30, 100, 13_000))
    }

    @Test
    fun nothingNewIsNeverShown() {
        val throttle = ProgressThrottle(3_000)
        throttle.shouldUpdate(10, 100, 10_000)
        assertFalse(throttle.shouldUpdate(10, 100, 99_000))
    }

    @Test
    fun aNewTotalIsShownAtOnce() {
        val throttle = ProgressThrottle(3_000)
        throttle.shouldUpdate(10, 100, 10_000)
        assertTrue(throttle.shouldUpdate(10, 250, 10_100))
    }

    @Test
    fun theIntervalRestartsFromTheLastShownUpdate() {
        val throttle = ProgressThrottle(3_000)
        throttle.shouldUpdate(0, 100, 0)
        assertFalse(throttle.shouldUpdate(1, 100, 2_000))
        assertTrue(throttle.shouldUpdate(2, 100, 3_000))
        assertFalse(throttle.shouldUpdate(3, 100, 4_000)) // only 1 s after the one just shown
        assertTrue(throttle.shouldUpdate(4, 100, 6_000))
    }
}
