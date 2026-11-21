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

    @Test
    fun photoRectIsTheWholeContainerAtRest() {
        val rect = ViewerDragMath.photoRect(1080, 2400, scale = 1f, offsetX = 0f, offsetY = 0f)
        assertEquals(PhotoRect(0f, 0f, 1080f, 2400f), rect)
    }

    @Test
    fun photoRectShrinksAboutTheCentreAndFollowsTheOffset() {
        // Half size: 540 x 1200, centred at (540, 1200), then moved by (100, -50).
        val rect = ViewerDragMath.photoRect(1080, 2400, scale = 0.5f, offsetX = 100f, offsetY = -50f)
        assertEquals(PhotoRect(370f, 550f, 910f, 1750f), rect)
    }

    @Test
    fun photoRectNeverCollapses() {
        val rect = ViewerDragMath.photoRect(1080, 2400, scale = 0f, offsetX = 0f, offsetY = 0f)
        assertEquals(1f, rect.right - rect.left, 0f)
        assertEquals(1f, rect.bottom - rect.top, 0f)
    }

    @Test
    fun closeFlightIsTheNormalOneForAGentleReleaseAndShorterForAHardFlick() {
        assertEquals(300, ViewerDragMath.closeMillis(0f, 300))
        assertEquals(201, ViewerDragMath.closeMillis(5000f, 300))
        assertEquals(201, ViewerDragMath.closeMillis(20000f, 300)) // capped
        // Half the top speed is halfway between the two, give or take a rounding.
        assertTrue(ViewerDragMath.closeMillis(2500f, 300) in 250..251)
        // Direction does not matter, only speed.
        assertEquals(ViewerDragMath.closeMillis(3000f, 300), ViewerDragMath.closeMillis(-3000f, 300))
    }

    @Test
    fun backSwipeMovesThePhotoTowardTheSideTheSwipeWent() {
        assertEquals(86.4f, ViewerDragMath.backEdgeShift(fromRightEdge = false, progress = 1f, containerWidth = 1080f), 0.01f)
        assertEquals(-86.4f, ViewerDragMath.backEdgeShift(fromRightEdge = true, progress = 1f, containerWidth = 1080f), 0.01f)
        assertEquals(0f, ViewerDragMath.backEdgeShift(fromRightEdge = true, progress = 0f, containerWidth = 1080f), 0f)
        // Half way is half the shift; progress outside 0..1 is held to it.
        assertEquals(43.2f, ViewerDragMath.backEdgeShift(false, 0.5f, 1080f), 0.01f)
        assertEquals(86.4f, ViewerDragMath.backEdgeShift(false, 2f, 1080f), 0.01f)
    }

    @Test
    fun fitSizeKeepsTheProportionsAndTouchesTheTighterSide() {
        val wide = ViewerDragMath.fitSize(1080f, 2400f, 4000, 3000)
        assertEquals(1080f, wide.width, 0.01f)
        assertEquals(810f, wide.height, 0.01f)
        val tall = ViewerDragMath.fitSize(1080f, 2400f, 1000, 4000)
        assertEquals(600f, tall.width, 0.01f)
        assertEquals(2400f, tall.height, 0.01f)
        // Unknown size: the whole container.
        assertEquals(PhotoSize(1080f, 2400f), ViewerDragMath.fitSize(1080f, 2400f, 0, 0))
    }

    @Test
    fun photoRectForFollowsScaleAndOffsetAboutTheCentre() {
        val photo = PhotoSize(1080f, 810f)
        val atRest = ViewerDragMath.photoRectFor(1080f, 2400f, photo, 1f, 0f, 0f)
        assertEquals(PhotoRect(0f, 795f, 1080f, 1605f), atRest)
        val pulled = ViewerDragMath.photoRectFor(1080f, 2400f, photo, 0.5f, 100f, 200f)
        assertEquals(PhotoRect(370f, 1197.5f, 910f, 1602.5f), pulled)
    }

    @Test
    fun theFlightEndsCoveringTheCellAndCentredOnIt() {
        val photo = PhotoSize(1080f, 810f)
        val cell = PhotoRect(700f, 300f, 1000f, 600f) // a 300 px square
        val end = ViewerDragMath.flightEnd(1080f, 2400f, photo, cell)
        // The photo is wider than tall: its height has to reach the cell's, so it is cropped on the sides.
        assertEquals(300f / 810f, end.scale, 0.0001f)
        val rect = ViewerDragMath.photoRectFor(1080f, 2400f, photo, end.scale, end.offsetX, end.offsetY)
        assertEquals(cell.centerX, rect.centerX, 0.01f)
        assertEquals(cell.centerY, rect.centerY, 0.01f)
        assertTrue(rect.left <= cell.left && rect.right >= cell.right)
        assertTrue(rect.top <= cell.top + 0.01f && rect.bottom >= cell.bottom - 0.01f)
    }

    @Test
    fun aTallPhotoIsCroppedAtTheTopAndBottomInASquareCell() {
        val photo = PhotoSize(600f, 2400f)
        val cell = PhotoRect(0f, 0f, 300f, 300f)
        val end = ViewerDragMath.flightEnd(1080f, 2400f, photo, cell)
        assertEquals(0.5f, end.scale, 0.0001f)
    }

    @Test
    fun lerpGivesTheEndsAndTheMiddle() {
        val a = PhotoRect(0f, 0f, 100f, 200f)
        val b = PhotoRect(100f, 100f, 200f, 300f)
        assertEquals(a, ViewerDragMath.lerp(a, b, 0f))
        assertEquals(b, ViewerDragMath.lerp(a, b, 1f))
        assertEquals(PhotoRect(50f, 50f, 150f, 250f), ViewerDragMath.lerp(a, b, 0.5f))
    }
}
