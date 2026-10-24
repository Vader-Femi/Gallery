package com.femi.gallery.presentation.viewer

import com.femi.gallery.presentation.viewer.ViewerDragMath.Release
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerDragMathTest {

    private val height = 2400f

    @Test
    fun progressGrowsWithTheDragAndStopsAtOne() {
        assertEquals(0f, ViewerDragMath.progress(0f, height), 0.0001f)
        assertEquals(0.5f, ViewerDragMath.progress(480f, height), 0.0001f) // 0.4 * 2400 = 960 for full
        assertEquals(1f, ViewerDragMath.progress(5_000f, height), 0.0001f)
        assertEquals(0f, ViewerDragMath.progress(-300f, height), 0.0001f) // pulling up is not progress
        assertEquals(0f, ViewerDragMath.progress(100f, 0f), 0.0001f)
    }

    @Test
    fun thePhotoShrinksAndTheBackgroundFadesAsItIsPulled() {
        assertEquals(1f, ViewerDragMath.scale(0f), 0.0001f)
        assertEquals(ViewerDragMath.MIN_SCALE, ViewerDragMath.scale(1f), 0.0001f)
        assertEquals(1f, ViewerDragMath.backgroundAlpha(0f), 0.0001f)
        assertEquals(0f, ViewerDragMath.backgroundAlpha(1f), 0.0001f)
        assertEquals(0.5f, ViewerDragMath.backgroundAlpha(0.5f), 0.0001f)
    }

    @Test
    fun theBarsDisappearQuickly() {
        assertEquals(1f, ViewerDragMath.chromeAlpha(0f), 0.0001f)
        assertEquals(0f, ViewerDragMath.chromeAlpha(0.2f), 0.0001f)
        assertEquals(0f, ViewerDragMath.chromeAlpha(1f), 0.0001f)
        assertTrue(ViewerDragMath.chromeAlpha(0.1f) in 0.4f..0.6f)
    }

    @Test
    fun pullingDownIsOneToOneAndPullingUpResists() {
        assertEquals(300f, ViewerDragMath.resistUp(300f, height), 0.0001f)
        val limit = height * ViewerDragMath.MAX_UP_FRACTION
        val small = ViewerDragMath.resistUp(-20f, height)
        assertTrue(small < 0f && small > -20f) // moves, but less than the finger
        val far = ViewerDragMath.resistUp(-10_000f, height)
        assertTrue(far < 0f && far >= -limit) // never past the limit
        assertTrue(ViewerDragMath.resistUp(-400f, height) < ViewerDragMath.resistUp(-200f, height)) // still moves
    }

    @Test
    fun aLongEnoughPullDownCloses() {
        val farEnough = ViewerDragMath.DISMISS_AT_PROGRESS * height * ViewerDragMath.FULL_DRAG_FRACTION
        assertEquals(Release.Dismiss, ViewerDragMath.release(farEnough + 1f, 0f, height))
        assertEquals(Release.Restore, ViewerDragMath.release(farEnough - 20f, 0f, height))
    }

    @Test
    fun aQuickFlickDownClosesFromAnyDistance() {
        assertEquals(Release.Dismiss, ViewerDragMath.release(40f, 2_500f, height))
        assertEquals(Release.Restore, ViewerDragMath.release(40f, 800f, height))
        // a downward flick with the photo not below rest is not a dismissal
        assertEquals(Release.Restore, ViewerDragMath.release(0f, 3_000f, height))
    }

    @Test
    fun pullingUpOpensTheInfo() {
        val pulledUp = -height * ViewerDragMath.INFO_AT_FRACTION
        assertEquals(Release.ShowInfo, ViewerDragMath.release(pulledUp - 1f, 0f, height))
        assertEquals(Release.Restore, ViewerDragMath.release(pulledUp + 20f, 0f, height))
        assertEquals(Release.ShowInfo, ViewerDragMath.release(-30f, -2_000f, height))
        assertEquals(Release.Restore, ViewerDragMath.release(-30f, -500f, height))
    }

    @Test
    fun anUntouchedPhotoStaysPut() {
        assertEquals(Release.Restore, ViewerDragMath.release(0f, 0f, height))
    }
}
