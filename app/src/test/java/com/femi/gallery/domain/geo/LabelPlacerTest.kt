package com.femi.gallery.domain.geo

import com.femi.gallery.domain.geo.LabelPlacer.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelPlacerTest {

    @Test
    fun theMoreImportantLabelWinsAnOverlap() {
        val shown = LabelPlacer.place(listOf(Box(0f, 0f, 50f, 20f), Box(40f, 5f, 90f, 25f)))
        assertEquals(listOf(true, false), shown.toList())
    }

    @Test
    fun labelsThatDoNotTouchAreAllShown() {
        val shown = LabelPlacer.place(listOf(Box(0f, 0f, 50f, 20f), Box(100f, 0f, 150f, 20f), Box(0f, 100f, 50f, 120f)))
        assertTrue(shown.all { it })
    }

    @Test
    fun paddingKeepsLabelsApart() {
        val boxes = listOf(Box(0f, 0f, 50f, 20f), Box(52f, 0f, 100f, 20f))
        assertEquals(listOf(true, false), LabelPlacer.place(boxes, padding = 4f).toList())
        assertEquals(listOf(true, true), LabelPlacer.place(boxes, padding = 0f).toList())
    }

    @Test
    fun aHiddenLabelDoesNotBlockOthers() {
        // The second overlaps the first and is hidden; the third overlaps only the hidden one.
        val shown = LabelPlacer.place(
            listOf(Box(0f, 0f, 50f, 20f), Box(40f, 0f, 90f, 20f), Box(95f, 0f, 140f, 20f)),
            padding = 0f,
        )
        assertEquals(listOf(true, false, true), shown.toList())
    }

    @Test
    fun noLabelsIsFine() {
        assertEquals(0, LabelPlacer.place(emptyList()).size)
    }
}
