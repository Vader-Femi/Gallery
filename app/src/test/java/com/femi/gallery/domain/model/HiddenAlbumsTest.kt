package com.femi.gallery.domain.model

import com.femi.gallery.domain.mirror.MirrorQuery
import com.femi.gallery.domain.search.SearchCriteria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenAlbumsTest {
    private val hidden = setOf(7L, 9L)

    @Test
    fun theWholeLibraryLeavesHiddenAlbumsOut() {
        assertEquals(hidden, HiddenAlbums.excludedFor(null, hidden))
    }

    @Test
    fun favoritesAndTheRecycleBinLeaveThemOutToo() {
        assertEquals(hidden, HiddenAlbums.excludedFor(SpecialAlbums.FAVORITES, hidden))
        assertEquals(hidden, HiddenAlbums.excludedFor(SpecialAlbums.TRASH, hidden))
    }

    @Test
    fun aRealFolderAskedForByIdIsReturnedAsItIs() {
        assertTrue(HiddenAlbums.excludedFor(7L, hidden).isEmpty())
        assertTrue(HiddenAlbums.excludedFor(42L, hidden).isEmpty())
    }

    @Test
    fun nothingHiddenExcludesNothing() {
        assertTrue(HiddenAlbums.excludedFor(null, emptySet()).isEmpty())
    }

    @Test
    fun theMirrorQueryCarriesTheExcludedAlbums() {
        val all = MirrorQuery.from(null, SearchCriteria(), HiddenAlbums.excludedFor(null, hidden))!!
        assertEquals(hidden, all.excludedBuckets.toSet())
        val album = MirrorQuery.from(42L, null, HiddenAlbums.excludedFor(42L, hidden))!!
        assertTrue(album.excludedBuckets.isEmpty())
    }
}
