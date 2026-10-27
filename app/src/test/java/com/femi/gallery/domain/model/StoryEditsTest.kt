package com.femi.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryEditsTest {

    private val trip = StoryEdit(100, 103, title = "Lagos trip")
    private val memory = StoryEdit(101, 101, title = "Birthday")

    @Test
    fun anExactRangeIsFound() {
        assertEquals(trip, StoryEdits.find(listOf(trip), 100, 103))
    }

    @Test
    fun aTripThatGrewKeepsItsEdit() {
        assertEquals(trip, StoryEdits.find(listOf(trip), 100, 105)) // 4 of 6 days
        assertEquals(trip, StoryEdits.find(listOf(trip), 99, 103))
    }

    @Test
    fun aTripThatChangedALotLosesItsEdit() {
        assertNull(StoryEdits.find(listOf(trip), 100, 112)) // 4 of 13 days
        assertNull(StoryEdits.find(listOf(trip), 200, 203))
    }

    @Test
    fun aSingleDayMatchesOnlyItself() {
        assertNull(StoryEdits.find(listOf(memory), 101, 102))
        assertNull(StoryEdits.find(listOf(memory), 100, 101))
        assertEquals(memory, StoryEdits.find(listOf(memory), 101, 101))
    }

    @Test
    fun aMemoryDoesNotTakeTheTripsTitle() {
        assertNull(StoryEdits.find(listOf(trip), 101, 101))
        assertEquals(trip, StoryEdits.find(listOf(trip, memory), 100, 103))
        assertEquals(memory, StoryEdits.find(listOf(trip, memory), 101, 101))
    }

    @Test
    fun assignGivesEachEditToOneStoryAndExactMatchesWin() {
        val grown = StoryEdit(100, 104, title = "Grown")
        val result = StoryEdits.assign(listOf(trip, grown), listOf(100L to 104L, 100L to 103L))
        assertEquals(grown, result[0])
        assertEquals(trip, result[1])
    }

    @Test
    fun assignDoesNotShareOneEditBetweenTwoStories() {
        val result = StoryEdits.assign(listOf(trip), listOf(100L to 104L, 99L to 103L))
        assertEquals(1, result.count { it != null })
    }

    @Test
    fun assignKeepsTheOrderOfTheStories() {
        val result = StoryEdits.assign(listOf(trip), listOf(300L to 301L, 100L to 103L))
        assertNull(result[0])
        assertEquals(trip, result[1])
    }

    @Test
    fun updateAddsAnEdit() {
        val updated = StoryEdits.update(emptyList(), 100, 103) { it.copy(title = "New") }
        assertEquals(listOf(StoryEdit(100, 103, "New", null)), updated)
    }

    @Test
    fun updateReplacesTheEditOfThatStoryAndWritesItsCurrentDays() {
        val updated = StoryEdits.update(listOf(trip), 100, 105) { it.copy(coverId = 7L) }
        assertEquals(listOf(StoryEdit(100, 105, "Lagos trip", 7L)), updated)
    }

    @Test
    fun updateLeavesOtherStoriesAlone() {
        val other = StoryEdit(500, 502, title = "Other")
        val updated = StoryEdits.update(listOf(trip, other), 100, 103) { it.copy(title = null, coverId = 5L) }
        assertTrue(other in updated)
        assertEquals(2, updated.size)
    }

    @Test
    fun anEditThatChangesNothingIsDropped() {
        val updated = StoryEdits.update(listOf(trip), 100, 103) { it.copy(title = null) }
        assertTrue(updated.isEmpty())
    }
}
