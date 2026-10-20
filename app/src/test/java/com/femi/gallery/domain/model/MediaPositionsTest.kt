package com.femi.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaPositionsTest {

    /** A list whose position `i` holds id `ids[i]`; null entries are "not loaded yet". */
    private fun find(
        ids: List<Long?>,
        preferred: Int,
        id: Long,
        maxDistance: Int = MediaPositions.DEFAULT_SEARCH_DISTANCE,
    ) = MediaPositions.find(preferred, id, ids.size, maxDistance) { ids.getOrNull(it) }

    private val ids: List<Long?> = (0 until 100).map { 1_000L + it }

    @Test
    fun keepsThePositionWhenItHoldsThePhoto() {
        assertEquals(40, find(ids, preferred = 40, id = 1_040L))
    }

    @Test
    fun findsThePhotoWhenTheListIsShiftedAbove() {
        // The position now holds a photo 30 places further up the list.
        assertEquals(70, find(ids, preferred = 40, id = 1_070L))
    }

    @Test
    fun findsThePhotoWhenTheListIsShiftedBelow() {
        assertEquals(15, find(ids, preferred = 40, id = 1_015L))
    }

    @Test
    fun prefersTheNearestPosition() {
        val duplicated = ids.toMutableList().apply { this[10] = 5L; this[60] = 5L }
        assertEquals(60, find(duplicated, preferred = 55, id = 5L))
        assertEquals(10, find(duplicated, preferred = 20, id = 5L))
    }

    @Test
    fun skipsPositionsThatAreNotLoaded() {
        val sparse = ids.mapIndexed { i, id -> if (i in 35..45) null else id }
        assertEquals(30, find(sparse, preferred = 40, id = 1_030L))
        assertNull(find(sparse, preferred = 40, id = 1_040L))
    }

    @Test
    fun returnsNullWhenThePhotoIsNotInTheList() {
        assertNull(find(ids, preferred = 40, id = 9_999L))
    }

    @Test
    fun returnsNullWithoutAnIdOrItems() {
        assertNull(find(ids, preferred = 40, id = MediaPositions.NO_ID))
        assertNull(find(emptyList(), preferred = 0, id = 1L))
    }

    @Test
    fun clampsAPreferredPositionOutsideTheList() {
        assertEquals(99, find(ids, preferred = 500, id = 1_099L))
        assertEquals(0, find(ids, preferred = -7, id = 1_000L))
    }

    @Test
    fun givesUpBeyondTheSearchDistance() {
        assertNull(find(ids, preferred = 10, id = 1_050L, maxDistance = 30))
        assertEquals(50, find(ids, preferred = 10, id = 1_050L, maxDistance = 40))
    }
}
