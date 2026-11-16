package com.femi.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumPaneSelectionTest {

    private fun album(id: Long) = Album(bucketId = id, name = "", relativePath = "", coverUri = "", itemCount = 1)

    @Test
    fun keepsAChoiceThatIsStillListed() {
        val albums = listOf(album(SpecialAlbums.LOCKED), album(7), album(9))
        assertEquals(9L, AlbumPaneSelection.resolve(9L, albums))
        assertEquals(SpecialAlbums.LOCKED, AlbumPaneSelection.resolve(SpecialAlbums.LOCKED, albums))
    }

    @Test
    fun startsOnTheFirstRealFolderNotOnASpecialAlbum() {
        val albums = listOf(
            album(SpecialAlbums.FAVORITES), album(SpecialAlbums.LOCKED), album(SpecialAlbums.PEOPLE), album(-5), album(7),
        )
        assertEquals(-5L, AlbumPaneSelection.resolve(null, albums))
    }

    @Test
    fun neverOpensLockedOrPeopleByItself() {
        val onlyLocked = listOf(album(SpecialAlbums.LOCKED), album(SpecialAlbums.PEOPLE))
        assertNull(AlbumPaneSelection.resolve(null, onlyLocked))
        val withTrash = listOf(album(SpecialAlbums.LOCKED), album(SpecialAlbums.TRASH))
        assertEquals(SpecialAlbums.TRASH, AlbumPaneSelection.resolve(null, withTrash))
    }

    @Test
    fun dropsAChoiceThatLeftTheList() {
        val albums = listOf(album(7), album(9))
        assertEquals(7L, AlbumPaneSelection.resolve(42L, albums))
        assertEquals(7L, AlbumPaneSelection.resolve(SpecialAlbums.LOCKED, albums))
    }

    @Test
    fun peopleIsNeverShownInThePane() {
        val albums = listOf(album(SpecialAlbums.PEOPLE), album(7))
        assertEquals(7L, AlbumPaneSelection.resolve(SpecialAlbums.PEOPLE, albums))
    }

    @Test
    fun emptyListShowsNothing() {
        assertNull(AlbumPaneSelection.resolve(7L, emptyList()))
    }
}
