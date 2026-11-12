package com.femi.gallery.domain.search

import com.femi.gallery.domain.search.SearchQueryEditor.Chip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SearchQueryEditorTest {
    private val today = LocalDate.of(2026, 11, 12)

    @Test
    fun aChipAddsItsWordAndTakesItOutAgain() {
        assertEquals("videos", SearchQueryEditor.toggle("", Chip.Videos))
        assertEquals("beach videos", SearchQueryEditor.toggle("beach", Chip.Videos))
        assertEquals("beach", SearchQueryEditor.toggle("beach videos", Chip.Videos))
    }

    @Test
    fun aChipThatShowsAsSelectedForAnotherSpellingIsTakenOutNotRewritten() {
        // Typing the singular used to make the chip look chosen, and tapping it changed the word to the plural.
        assertTrue(SearchQueryEditor.isSelected("beach video", Chip.Videos))
        assertEquals("beach", SearchQueryEditor.toggle("beach video", Chip.Videos))
        assertEquals("sunset", SearchQueryEditor.toggle("sunset Favourite", Chip.Favorites))
        assertEquals("dogs", SearchQueryEditor.toggle("dogs picture", Chip.Photos))
        assertEquals("", SearchQueryEditor.toggle("screenshot", Chip.Screenshots))
    }

    @Test
    fun videosAndPhotosReplaceEachOther() {
        assertEquals("beach photos", SearchQueryEditor.toggle("beach videos", Chip.Photos))
        assertEquals("beach videos", SearchQueryEditor.toggle("beach photo", Chip.Videos))
        assertEquals("videos", SearchQueryEditor.toggle("photos", Chip.Videos))
    }

    @Test
    fun otherChipsLeaveTheRestOfTheQueryAlone() {
        assertEquals("beach videos favorites", SearchQueryEditor.toggle("beach videos", Chip.Favorites))
        assertEquals("beach videos", SearchQueryEditor.toggle("beach videos favorites", Chip.Favorites))
    }

    @Test
    fun theWordsOfAChipAreOnlyFoundAsWholeWords() {
        assertFalse(SearchQueryEditor.isSelected("videographer", Chip.Videos))
        assertFalse(SearchQueryEditor.isSelected("photography", Chip.Photos))
        assertFalse(SearchQueryEditor.isSelected("favoritesque", Chip.Favorites))
    }

    @Test
    fun last30IsOnlyChosenForItsOwnDate() {
        assertTrue(SearchQueryEditor.isLast30("beach from:2026-10-13", today))
        assertFalse(SearchQueryEditor.isLast30("beach from:2024-01-01", today))
        assertFalse(SearchQueryEditor.isLast30("beach", today))
    }

    @Test
    fun last30IsSetAndRemoved() {
        assertEquals("from:2026-10-13", SearchQueryEditor.toggleLast30("", today))
        assertEquals("beach from:2026-10-13", SearchQueryEditor.toggleLast30("beach", today))
        assertEquals("beach", SearchQueryEditor.toggleLast30("beach from:2026-10-13", today))
    }

    @Test
    fun last30ReplacesADateOfThePersonsOwnInsteadOfAddingASecond() {
        assertEquals("beach from:2026-10-13", SearchQueryEditor.toggleLast30("beach from:2024-01-01", today))
        assertEquals("beach to:2026-02-01 from:2026-10-13", SearchQueryEditor.toggleLast30("beach from:2024-01-01 to:2026-02-01", today))
    }
}
