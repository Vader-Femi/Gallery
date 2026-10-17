package com.femi.gallery.presentation.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerDragToDismissStateTest {

    @Test
    fun calculateDragProgressClampsToUnitRange() {
        assertEquals(0f, ViewerDragState.calculateDragProgress(0f, 600f), 0.001f)
        assertEquals(0.5f, ViewerDragState.calculateDragProgress(300f, 600f), 0.001f)
        assertEquals(1.0f, ViewerDragState.calculateDragProgress(600f, 600f), 0.001f)
        assertEquals(1.0f, ViewerDragState.calculateDragProgress(1200f, 600f), 0.001f)
        assertEquals(0.5f, ViewerDragState.calculateDragProgress(-300f, 600f), 0.001f)
    }

    @Test
    fun calculateScaleDownscalesWithDragProgress() {
        assertEquals(1.0f, ViewerDragState.calculateScale(0f), 0.001f)
        assertEquals(0.825f, ViewerDragState.calculateScale(0.5f), 0.001f)
        assertEquals(0.65f, ViewerDragState.calculateScale(1.0f), 0.001f)
    }

    @Test
    fun calculateScrimAlphaFadesProportionalToProgress() {
        assertEquals(1.0f, ViewerDragState.calculateScrimAlpha(0f), 0.001f)
        assertEquals(0.4f, ViewerDragState.calculateScrimAlpha(0.5f), 0.001f)
        assertEquals(0.0f, ViewerDragState.calculateScrimAlpha(1.0f), 0.001f)
    }

    @Test
    fun calculateCornerRadiusDpIncreasesWithProgress() {
        assertEquals(0f, ViewerDragState.calculateCornerRadiusDp(0f), 0.001f)
        assertEquals(14f, ViewerDragState.calculateCornerRadiusDp(0.5f), 0.001f)
        assertEquals(28f, ViewerDragState.calculateCornerRadiusDp(1.0f), 0.001f)
    }

    @Test
    fun shouldDismissOnReleaseTriggersWhenPastDistanceThresholdOrFlingVelocity() {
        // Distance threshold crossed
        assertTrue(
            ViewerDragState.shouldDismissOnRelease(
                totalYPx = 200f,
                velocityYPxPerSec = 100f,
                dismissThresholdPx = 160f,
                flingVelocityThresholdPx = 1000f,
            ),
        )

        // Fling velocity threshold crossed
        assertTrue(
            ViewerDragState.shouldDismissOnRelease(
                totalYPx = 80f,
                velocityYPxPerSec = 1200f,
                dismissThresholdPx = 160f,
                flingVelocityThresholdPx = 1000f,
            ),
        )

        // Neither threshold crossed -> snap back
        assertFalse(
            ViewerDragState.shouldDismissOnRelease(
                totalYPx = 80f,
                velocityYPxPerSec = 400f,
                dismissThresholdPx = 160f,
                flingVelocityThresholdPx = 1000f,
            ),
        )
    }
}
