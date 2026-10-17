package com.femi.gallery.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaOrderingTest {

    @Test
    fun takenDateWinsOverAddedDate() {
        assertEquals(1_000L, MediaOrdering.effectiveDateMillis(taken = 1_000L, addedSeconds = 999_999L))
    }

    @Test
    fun missingTakenDateFallsBackToAddedDateInMillis() {
        assertEquals(5_000L, MediaOrdering.effectiveDateMillis(taken = 0L, addedSeconds = 5L))
        assertEquals(5_000L, MediaOrdering.effectiveDateMillis(taken = -1L, addedSeconds = 5L))
    }

    @Test
    fun newestFirstSortsByEffectiveDateNotByAddedDate() {
        // A restored old photo: added today (id 30) but taken long ago, between two older photos.
        val ids = longArrayOf(10, 20, 30, 40)
        val dates = longArrayOf(
            MediaOrdering.effectiveDateMillis(taken = 4_000L, addedSeconds = 4),   // 10
            MediaOrdering.effectiveDateMillis(taken = 0L, addedSeconds = 3),       // 20 (no date taken)
            MediaOrdering.effectiveDateMillis(taken = 2_000L, addedSeconds = 9999), // 30 restored
            MediaOrdering.effectiveDateMillis(taken = 1_000L, addedSeconds = 1),   // 40
        )

        val order = MediaOrdering.newestFirst(ids, dates)

        assertArrayEquals(longArrayOf(10, 20, 30, 40), order.ids)
        assertArrayEquals(longArrayOf(4_000, 3_000, 2_000, 1_000), order.datesMillis)
    }

    @Test
    fun equalDatesFallBackToHigherIdFirst() {
        val order = MediaOrdering.newestFirst(longArrayOf(1, 3, 2), longArrayOf(5, 5, 5))
        assertArrayEquals(longArrayOf(3, 2, 1), order.ids)
    }

    @Test
    fun onlyTheFirstCountItemsAreUsed() {
        val order = MediaOrdering.newestFirst(longArrayOf(1, 2, 99), longArrayOf(1, 2, 3), count = 2)
        assertArrayEquals(longArrayOf(2, 1), order.ids)
        assertEquals(2, order.size)
    }
}
