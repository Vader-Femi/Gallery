package com.femi.gallery.domain.face

import com.femi.gallery.data.db.PersonRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleVisibilityTest {
    private fun person(id: Long, cover: Long, count: Int = 10, crop: String = "0,0,1,1") =
        PersonRow(id = id, name = "Person $id", coverMediaId = cover, coverCrop = crop, photoCount = count)

    @Test
    fun aPersonWithNoVisiblePhotoIsLeftOut() {
        val result = PeopleVisibility.apply(listOf(person(1, 100), person(2, 200)), mapOf(1L to 4), emptySet(), emptyMap())
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test
    fun theCountIsOfTheVisiblePhotosOnly() {
        val result = PeopleVisibility.apply(listOf(person(1, 100, count = 10)), mapOf(1L to 3), emptySet(), emptyMap())
        assertEquals(3, result.single().photoCount)
        assertEquals(100L, result.single().coverMediaId)
    }

    @Test
    fun aCoverInAHiddenAlbumIsReplaced() {
        val result = PeopleVisibility.apply(
            persons = listOf(person(1, 100, crop = "0.1,0.1,0.2,0.2")),
            visibleCounts = mapOf(1L to 2),
            hiddenCoverPersons = setOf(1L),
            replacements = mapOf(1L to PeopleVisibility.Cover(555L, "0.3,0.3,0.4,0.4")),
        )
        assertEquals(555L, result.single().coverMediaId)
        assertEquals("0.3,0.3,0.4,0.4", result.single().coverCrop)
        assertEquals(2, result.single().photoCount)
    }

    @Test
    fun aCoverThatCannotBeReplacedLeavesThePersonOut() {
        val result = PeopleVisibility.apply(listOf(person(1, 100)), mapOf(1L to 2), setOf(1L), emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun peopleWhoseCoverIsFineKeepTheirOwnCoverAndOrder() {
        val result = PeopleVisibility.apply(
            persons = listOf(person(3, 30), person(1, 10), person(2, 20)),
            visibleCounts = mapOf(1L to 5, 2L to 6, 3L to 7),
            hiddenCoverPersons = setOf(2L),
            replacements = mapOf(2L to PeopleVisibility.Cover(99L, "0,0,0.5,0.5")),
        )
        assertEquals(listOf(3L, 1L, 2L), result.map { it.id })
        assertEquals(listOf(30L, 10L, 99L), result.map { it.coverMediaId })
    }
}
