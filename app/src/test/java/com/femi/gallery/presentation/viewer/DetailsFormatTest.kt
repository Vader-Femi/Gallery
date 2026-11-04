package com.femi.gallery.presentation.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailsFormatTest {

    @Test
    fun formatsIntegerFps() {
        assertEquals("30 fps", formatFps(30.0))
        assertEquals("60 fps", formatFps(60.0))
        assertEquals("24 fps", formatFps(24.0))
        assertEquals("120 fps", formatFps(120.0))
    }

    @Test
    fun formatsFractionalFpsNearInteger() {
        assertEquals("30 fps", formatFps(29.98))
        assertEquals("60 fps", formatFps(59.98))
    }

    @Test
    fun formatsStandardBroadcastRatesToCleanFps() {
        assertEquals("30 fps", formatFps(29.97))
        assertEquals("60 fps", formatFps(59.94))
        assertEquals("24 fps", formatFps(23.976))
    }

    @Test
    fun formatsCustomFractionalFps() {
        assertEquals("14.5 fps", formatFps(14.5))
    }

    @Test
    fun formatsExposure() {
        assertEquals("1/120s", formatExposure(0.0083333333))
        assertEquals("2s", formatExposure(2.0))
        assertEquals("1/2s", formatExposure(0.5))
    }

    @Test
    fun formatsResolution() {
        assertEquals("4080 × 3072 · 12.5 MP", formatResolution(4080, 3072))
        assertEquals("1920 × 1080 · 2.1 MP", formatResolution(1920, 1080))
    }

    @Test
    fun formatsDuration() {
        assertEquals("0:15", formatDuration(15_000L))
        assertEquals("1:05", formatDuration(65_000L))
        assertEquals("1:01:05", formatDuration(3665_000L))
    }
}
