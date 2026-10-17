package com.femi.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumPathsTest {

    @Test
    fun sanitizeRemovesForbiddenCharactersAndTrims() {
        assertEquals("Holiday 2025", AlbumPaths.sanitizeName("  Holiday 2025  "))
        assertEquals("ab", AlbumPaths.sanitizeName("a/b\\"))
        assertEquals("hidden", AlbumPaths.sanitizeName("..hidden"))
        assertEquals("name", AlbumPaths.sanitizeName("na:me?"))
    }

    @Test
    fun sanitizeRejectsEmptyResults() {
        assertNull(AlbumPaths.sanitizeName(""))
        assertNull(AlbumPaths.sanitizeName("   "))
        assertNull(AlbumPaths.sanitizeName("///"))
        assertNull(AlbumPaths.sanitizeName("..."))
    }

    @Test
    fun sanitizeLimitsLength() {
        assertEquals(60, AlbumPaths.sanitizeName("a".repeat(100))!!.length)
    }

    @Test
    fun newAlbumsLiveUnderDcim() {
        assertEquals("DCIM/Trips/", AlbumPaths.newAlbumPath("Trips"))
    }

    @Test
    fun renamingKeepsTheParentFolder() {
        assertEquals("Pictures/", AlbumPaths.parent("Pictures/Trips/"))
        assertEquals("DCIM/Camera/", AlbumPaths.parent("DCIM/Camera/Raw/"))
        assertEquals("", AlbumPaths.parent("Pictures/"))
        assertEquals("Pictures/Holiday/", AlbumPaths.renamed("Pictures/Trips/", "Holiday"))
    }

    @Test
    fun onlyRealAlbumFoldersCanBeTargetsOrRenamed() {
        assertTrue(AlbumPaths.isAlbumFolder("Pictures/Trips/"))
        assertTrue(AlbumPaths.isAlbumFolder("DCIM/Camera/"))
        assertFalse(AlbumPaths.isAlbumFolder("Pictures/"))
        assertFalse(AlbumPaths.isAlbumFolder("Android/media/com.whatsapp/WhatsApp Images/"))
        assertFalse(AlbumPaths.isAlbumFolder(""))

        assertTrue(AlbumPaths.canRename("Pictures/Trips/"))
        assertFalse(AlbumPaths.canRename("DCIM/Camera/"))
        assertFalse(AlbumPaths.canRename("Android/media/x/y/"))
    }

    @Test
    fun videosAndPhotosGetAllowedRoots() {
        assertEquals("Pictures/Trips/", AlbumPaths.forMedia("Pictures/Trips/", isVideo = false))
        assertEquals("Movies/Trips/", AlbumPaths.forMedia("Pictures/Trips/", isVideo = true))
        assertEquals("Pictures/Edits/", AlbumPaths.forMedia("Movies/Edits/", isVideo = false))
        assertEquals("DCIM/Trips/", AlbumPaths.forMedia("DCIM/Trips/", isVideo = true))
    }
}
