package com.femi.gallery.domain.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkupMathTest {
    private fun text(id: Long, x: Float, y: Float, size: Float = 0.1f, rotation: Float = 0f, content: String = "Hello") =
        MarkupText(id, content, MarkupPoint(x, y), size, rotation)

    private fun sticker(id: Long, x: Float, y: Float, size: Float = 0.2f, rotation: Float = 0f) =
        MarkupSticker(id, "😀", MarkupPoint(x, y), size, rotation)

    @Test
    fun simplifyKeepsTheEndsAndDropsClosePoints() {
        val points = (0..100).map { MarkupPoint(it / 1000f, 0.5f) } // 0 to 0.1 across a 1000 px wide photo
        val simplified = MarkupMath.simplify(points, minDistance = 0.01f, imageWidth = 1000f, imageHeight = 1000f)
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
        assertTrue(simplified.size in 8..14) // one point per 10 px, about 100 px long
        assertEquals(points.take(2), MarkupMath.simplify(points.take(2), 0.5f, 100f, 100f))
    }

    @Test
    fun simplifyMeasuresInPixelsNotFractions() {
        // The same fractions mean more pixels on a wide photo.
        val points = listOf(MarkupPoint(0f, 0f), MarkupPoint(0.01f, 0f), MarkupPoint(0.02f, 0f), MarkupPoint(1f, 0f))
        assertEquals(4, MarkupMath.simplify(points, 0.005f, imageWidth = 4000f, imageHeight = 1000f).size)
        assertEquals(2, MarkupMath.simplify(points, 0.5f, imageWidth = 4000f, imageHeight = 1000f).size)
    }

    @Test
    fun transformMovesScalesAndTurnsAnItem() {
        val moved = MarkupMath.transform(text(1, 0.5f, 0.5f, size = 0.1f), dx = 0.1f, dy = -0.2f, zoom = 2f, rotationDegrees = 30f) as MarkupText
        assertEquals(0.6f, moved.center.x, 1e-6f)
        assertEquals(0.3f, moved.center.y, 1e-6f)
        assertEquals(0.2f, moved.sizeFraction, 1e-6f)
        assertEquals(30f, moved.rotationDegrees, 1e-6f)
    }

    @Test
    fun anItemCannotLeaveThePhotoOrBecomeTinyOrHuge() {
        val out = MarkupMath.transform(sticker(1, 0.9f, 0.1f), 0.5f, -0.5f, 1f, 0f) as MarkupSticker
        assertEquals(1f, out.center.x, 0f)
        assertEquals(0f, out.center.y, 0f)
        assertEquals(MarkupMath.MIN_SIZE, (MarkupMath.transform(sticker(1, 0.5f, 0.5f), 0f, 0f, 0.001f, 0f) as MarkupSticker).sizeFraction, 0f)
        assertEquals(MarkupMath.MAX_SIZE, (MarkupMath.transform(sticker(1, 0.5f, 0.5f), 0f, 0f, 1000f, 0f) as MarkupSticker).sizeFraction, 0f)
    }

    @Test
    fun rotationStaysWithin180Degrees() {
        assertEquals(-170f, MarkupMath.normalizeDegrees(190f), 1e-4f)
        assertEquals(170f, MarkupMath.normalizeDegrees(-190f), 1e-4f)
        assertEquals(180f, MarkupMath.normalizeDegrees(180f), 1e-4f)
        assertEquals(0f, MarkupMath.normalizeDegrees(720f), 1e-4f)
        val turned = MarkupMath.transform(sticker(1, 0.5f, 0.5f, rotation = 170f), 0f, 0f, 1f, 30f) as MarkupSticker
        assertEquals(-160f, turned.rotationDegrees, 1e-4f)
    }

    @Test
    fun strokesAreNotMoved() {
        val stroke = MarkupStroke(1, listOf(MarkupPoint(0.1f, 0.1f), MarkupPoint(0.2f, 0.2f)), MarkupPalette.White, 0.01f)
        assertEquals(stroke, MarkupMath.transform(stroke, 0.3f, 0.3f, 2f, 45f))
    }

    @Test
    fun aTouchHitsTheStickerUnderIt() {
        val items = listOf(sticker(1, 0.5f, 0.5f, size = 0.2f))
        assertEquals(1L, MarkupMath.itemAt(items, 0.5f, 0.5f, 1000f, 1000f))
        assertEquals(1L, MarkupMath.itemAt(items, 0.59f, 0.5f, 1000f, 1000f)) // inside: 0.1 either side
        assertNull(MarkupMath.itemAt(items, 0.7f, 0.5f, 1000f, 1000f))
    }

    @Test
    fun theItemDrawnLastIsHitFirst() {
        val items = listOf(sticker(1, 0.5f, 0.5f), sticker(2, 0.5f, 0.5f))
        assertEquals(2L, MarkupMath.itemAt(items, 0.5f, 0.5f, 1000f, 1000f))
    }

    @Test
    fun aTurnedItemIsHitWhereItIs() {
        // A wide text turned a quarter: its long side is now vertical.
        val items = listOf(text(1, 0.5f, 0.5f, size = 0.1f, rotation = 90f, content = "Wide wide text"))
        assertEquals(1L, MarkupMath.itemAt(items, 0.5f, 0.7f, 1000f, 1000f, slop = 0f))
        assertNull(MarkupMath.itemAt(items, 0.9f, 0.5f, 1000f, 1000f, slop = 0f))
    }

    @Test
    fun smallItemsGetSomeSlop() {
        val items = listOf(sticker(1, 0.5f, 0.5f, size = 0.02f))
        assertNull(MarkupMath.itemAt(items, 0.55f, 0.5f, 1000f, 1000f, slop = 0f))
        assertEquals(1L, MarkupMath.itemAt(items, 0.53f, 0.5f, 1000f, 1000f, slop = 0.03f))
    }

    @Test
    fun strokesAreNeverSelected() {
        val stroke = MarkupStroke(1, listOf(MarkupPoint(0.5f, 0.5f)), MarkupPalette.White, 0.01f)
        assertNull(MarkupMath.itemAt(listOf(stroke), 0.5f, 0.5f, 100f, 100f))
    }

    @Test
    fun textHasTheSizeOfItsLines() {
        val (w, h) = MarkupMath.boxSize(text(1, 0.5f, 0.5f, size = 0.1f, content = "ab\nabcd"), 1000f)
        assertEquals(4 * 100f * 0.6f, w, 1e-3f)
        assertEquals(2 * 100f * 1.2f, h, 1e-3f)
    }

    @Test
    fun idsAreNeverReused() {
        assertEquals(1L, MarkupMath.nextId(emptyList()))
        assertEquals(8L, MarkupMath.nextId(listOf(sticker(3, 0f, 0f), sticker(7, 0f, 0f))))
    }
}
