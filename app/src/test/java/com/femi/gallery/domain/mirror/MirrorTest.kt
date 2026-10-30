package com.femi.gallery.domain.mirror

import com.femi.gallery.domain.geo.GeoBox
import com.femi.gallery.domain.model.MediaOrder
import com.femi.gallery.domain.model.MediaOrdering
import com.femi.gallery.domain.model.MediaType
import com.femi.gallery.domain.model.SpecialAlbums
import com.femi.gallery.domain.search.SearchCriteria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorQueryTest {

    @Test
    fun theWholeLibraryIsAnswered() {
        val q = MirrorQuery.from(null, null)!!
        assertNull(q.bucketId)
        assertNull(q.mediaType)
        assertTrue(!q.favoritesOnly && q.dateRanges.isEmpty())
    }

    @Test
    fun anAlbumIsAnswered() {
        assertEquals(42L, MirrorQuery.from(42L, null)!!.bucketId)
    }

    @Test
    fun theFavoritesAlbumIsAFilterNotAFolder() {
        val q = MirrorQuery.from(SpecialAlbums.FAVORITES, null)!!
        assertNull(q.bucketId)
        assertTrue(q.favoritesOnly)
    }

    @Test
    fun theRecycleBinAndTheLockedFolderAreNot() {
        assertNull(MirrorQuery.from(SpecialAlbums.TRASH, null))
        assertNull(MirrorQuery.from(SpecialAlbums.LOCKED, null))
    }

    @Test
    fun typeFavoritesAndDatesAreAnswered() {
        val search = SearchCriteria(mediaType = MediaType.Video, favoritesOnly = true, dateRanges = listOf(10L..20L))
        val q = MirrorQuery.from(7L, search)!!
        assertEquals(7L, q.bucketId)
        assertEquals(MediaType.Video, q.mediaType)
        assertTrue(q.favoritesOnly)
        assertEquals(listOf(10L..20L), q.dateRanges)
    }

    @Test
    fun wordsAndAreasAreLeftToMediaStore() {
        assertNull(MirrorQuery.from(null, SearchCriteria(terms = listOf("dog"))))
        assertNull(MirrorQuery.from(null, SearchCriteria(area = GeoBox(0.0, 0.0, 1.0, 1.0))))
    }

    @Test
    fun anEmptySearchIsTheWholeLibrary() {
        assertNotNull(MirrorQuery.from(null, SearchCriteria.None))
    }
}

class MirrorSyncTest {

    @Test
    fun neverCopiedMeansImport() {
        assertEquals(MirrorSync.Action.Import, MirrorSync.decide(null, 100))
    }

    @Test
    fun aNewerGenerationMeansUpdate() {
        assertEquals(MirrorSync.Action.Update, MirrorSync.decide(100, 101))
    }

    @Test
    fun theSameGenerationMeansNothingToDo() {
        assertEquals(MirrorSync.Action.Nothing, MirrorSync.decide(100, 100))
    }

    @Test
    fun aGenerationThatWentBackwardsMeansStartingAgain() {
        // The volume was reset (for instance the storage was wiped): do not trust the copy.
        assertEquals(MirrorSync.Action.Import, MirrorSync.decide(100, 5))
    }

    @Test
    fun reconcileFindsWhatIsGoneAndWhatIsMissing() {
        val r = MirrorSync.reconcile(mediaStoreIds = longArrayOf(1, 2, 4), mirrorIds = longArrayOf(1, 2, 3))
        assertEquals(listOf(3L), r.toDelete.toList())
        assertEquals(listOf(4L), r.missing.toList())
    }

    @Test
    fun reconcileOfEqualListsIsEmpty() {
        assertTrue(MirrorSync.reconcile(longArrayOf(3, 1, 2), longArrayOf(1, 2, 3)).isEmpty)
    }

    @Test
    fun reconcileWithAnEmptyMirrorMissesEverything() {
        val r = MirrorSync.reconcile(longArrayOf(1, 2), LongArray(0))
        assertEquals(listOf(1L, 2L), r.missing.toList())
        assertTrue(r.toDelete.isEmpty())
    }
}

class AlbumSortingTest {
    private fun album(id: Long, path: String, newest: Long) = AlbumSummary(id, "n$id", path, "uri$id", 1, newest)

    @Test
    fun theMostRecentAlbumIsFirst() {
        val sorted = AlbumSorting.order(listOf(album(1, "Pictures/A/", 10), album(2, "Pictures/B/", 30), album(3, "Pictures/C/", 20)))
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.bucketId })
    }

    @Test
    fun theCameraFolderIsAlwaysOnTop() {
        val sorted = AlbumSorting.order(listOf(album(1, "Pictures/A/", 99), album(2, AlbumSorting.CAMERA_PATH, 1), album(3, "Pictures/C/", 50)))
        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.bucketId })
    }
}

class DateRangeFilterTest {
    private val order = MediaOrder(
        ids = longArrayOf(4, 3, 2, 1),
        datesMillis = longArrayOf(4_500, 3_000, 2_999, 1_000),
    )

    @Test
    fun noRangesKeepsEverything() {
        assertEquals(4, MediaOrdering.withinDateRanges(order, emptyList()).size)
    }

    @Test
    fun aRangeKeepsItsSecondsBothEndsIncluded() {
        // seconds 2..3 are milliseconds 2000..3999
        val kept = MediaOrdering.withinDateRanges(order, listOf(2L..3L))
        assertEquals(listOf(3L, 2L), kept.ids.toList())
        assertEquals(listOf(3_000L, 2_999L), kept.datesMillis.toList())
    }

    @Test
    fun anItemJustPastTheLastSecondIsLeftOut() {
        val kept = MediaOrdering.withinDateRanges(order, listOf(3L..3L))
        assertEquals(listOf(3L), kept.ids.toList()) // 4500 ms is second 4
    }

    @Test
    fun severalRangesAreOred() {
        val kept = MediaOrdering.withinDateRanges(order, listOf(1L..1L, 4L..4L))
        assertEquals(listOf(4L, 1L), kept.ids.toList())
    }
}
