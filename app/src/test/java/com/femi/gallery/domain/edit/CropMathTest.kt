package com.femi.gallery.domain.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropMathTest {

    private fun assertRect(expected: CropRect, actual: CropRect) {
        assertEquals(expected.left, actual.left, 0.001f)
        assertEquals(expected.top, actual.top, 0.001f)
        assertEquals(expected.right, actual.right, 0.001f)
        assertEquals(expected.bottom, actual.bottom, 0.001f)
    }

    private fun pixelAspect(rect: CropRect, w: Int, h: Int) = rect.width * w / (rect.height * h)

    @Test
    fun noAspectMeansTheWholeImage() {
        assertTrue(CropMath.fitAspect(4000, 3000, null).isFull)
    }

    @Test
    fun squareInALandscapeImageIsCenteredAndFullHeight() {
        val square = CropMath.fitAspect(4000, 2000, 1f)
        assertRect(CropRect(0.25f, 0f, 0.75f, 1f), square)
        assertEquals(1f, pixelAspect(square, 4000, 2000), 0.001f)
    }

    @Test
    fun wideCropInAPortraitImageIsFullWidth() {
        val wide = CropMath.fitAspect(2000, 4000, 16f / 9f)
        assertEquals(1f, wide.width, 0.001f)
        assertEquals(16f / 9f, pixelAspect(wide, 2000, 4000), 0.001f)
        assertEquals((1f - wide.height) / 2f, wide.top, 0.001f)
    }

    @Test
    fun moveStaysInsideTheImage() {
        val rect = CropRect(0.2f, 0.2f, 0.6f, 0.6f)
        assertRect(CropRect(0.6f, 0.2f, 1f, 0.6f), CropMath.move(rect, 5f, 0f))
        assertRect(CropRect(0f, 0f, 0.4f, 0.4f), CropMath.move(rect, -5f, -5f))
        assertRect(CropRect(0.3f, 0.25f, 0.7f, 0.65f), CropMath.move(rect, 0.1f, 0.05f))
    }

    @Test
    fun freeCornerDragKeepsTheOppositeCornerFixed() {
        val rect = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
        val dragged = CropMath.dragCorner(rect, CropCorner.TopLeft, 0.2f, 0.1f, 1000, 1000)
        assertRect(CropRect(0.3f, 0.2f, 0.9f, 0.9f), dragged)
        val other = CropMath.dragCorner(rect, CropCorner.BottomRight, -0.3f, -0.2f, 1000, 1000)
        assertRect(CropRect(0.1f, 0.1f, 0.6f, 0.7f), other)
    }

    @Test
    fun cornerCannotLeaveTheImageOrShrinkBelowTheMinimum() {
        val rect = CropRect(0.1f, 0.1f, 0.9f, 0.9f)
        val outside = CropMath.dragCorner(rect, CropCorner.TopLeft, -5f, -5f, 1000, 1000)
        assertRect(CropRect(0f, 0f, 0.9f, 0.9f), outside)
        val tiny = CropMath.dragCorner(rect, CropCorner.BottomRight, -5f, -5f, 1000, 1000)
        assertEquals(CropMath.MIN_SIZE, tiny.width, 0.001f)
        assertEquals(CropMath.MIN_SIZE, tiny.height, 0.001f)
        assertEquals(0.1f, tiny.left, 0.001f)
    }

    @Test
    fun lockedAspectIsKeptWhileDragging() {
        val start = CropMath.fitAspect(4000, 3000, 1f)
        for (corner in CropCorner.entries) {
            val dragged = CropMath.dragCorner(start, corner, 0.07f, -0.13f, 4000, 3000, aspect = 1f)
            assertEquals("corner $corner", 1f, pixelAspect(dragged, 4000, 3000), 0.01f)
            assertTrue(dragged.left >= -0.0001f && dragged.top >= -0.0001f)
            assertTrue(dragged.right <= 1.0001f && dragged.bottom <= 1.0001f)
        }
    }

    @Test
    fun lockedAspectNeverEscapesTheImageWhenDraggedFarOut() {
        val start = CropRect(0.3f, 0.3f, 0.7f, 0.7f)
        for (corner in CropCorner.entries) {
            val dragged = CropMath.dragCorner(start, corner, 9f, 9f, 4000, 3000, aspect = 4f / 3f)
            assertTrue(dragged.left >= -0.0001f && dragged.top >= -0.0001f)
            assertTrue(dragged.right <= 1.0001f && dragged.bottom <= 1.0001f)
            assertEquals(4f / 3f, pixelAspect(dragged, 4000, 3000), 0.01f)
        }
    }
}
