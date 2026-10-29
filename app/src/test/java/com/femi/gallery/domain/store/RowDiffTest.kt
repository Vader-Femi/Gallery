package com.femi.gallery.domain.store

import com.femi.gallery.domain.text.DetectedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RowDiffTest {

    private fun text(id: Long, modified: Long = 1L, text: String = "t$id") = DetectedText(id, modified, text)
    private fun saved(vararg rows: DetectedText) = rows.associateBy { it.id }
    private fun diff(saved: Map<Long, DetectedText>, now: List<DetectedText>) = RowDiff.between(saved, now) { it.id }

    @Test
    fun nothingChangedMeansNothingToWrite() {
        val changes = diff(saved(text(1), text(2)), listOf(text(1), text(2)))
        assertTrue(changes.isEmpty)
    }

    @Test
    fun newRowsAreWritten() {
        val changes = diff(saved(text(1)), listOf(text(1), text(2), text(3)))
        assertEquals(listOf(text(2), text(3)), changes.changed)
        assertTrue(changes.removedIds.isEmpty())
    }

    @Test
    fun changedRowsAreWritten() {
        val changes = diff(saved(text(1, modified = 5), text(2)), listOf(text(1, modified = 9), text(2)))
        assertEquals(listOf(text(1, modified = 9)), changes.changed)
    }

    @Test
    fun missingRowsAreRemoved() {
        val changes = diff(saved(text(1), text(2), text(3)), listOf(text(2)))
        assertTrue(changes.changed.isEmpty())
        assertEquals(setOf(1L, 3L), changes.removedIds.toSet())
    }

    @Test
    fun everythingAtOnce() {
        val changes = diff(saved(text(1), text(2), text(3)), listOf(text(2, text = "new text"), text(4)))
        assertEquals(listOf(text(2, text = "new text"), text(4)), changes.changed)
        assertEquals(setOf(1L, 3L), changes.removedIds.toSet())
    }

    @Test
    fun startingFromNothingWritesEverything() {
        val changes = diff(emptyMap(), listOf(text(1), text(2)))
        assertEquals(2, changes.changed.size)
        assertTrue(changes.removedIds.isEmpty())
    }

    @Test
    fun emptyingTheListRemovesEverything() {
        val changes = diff(saved(text(1), text(2)), emptyList())
        assertTrue(changes.changed.isEmpty())
        assertEquals(setOf(1L, 2L), changes.removedIds.toSet())
    }

    @Test
    fun theLastRowWithAnIdWins() {
        val changes = diff(emptyMap(), listOf(text(1, text = "first"), text(1, text = "second")))
        assertEquals(listOf(text(1, text = "second")), changes.changed)
    }
}
