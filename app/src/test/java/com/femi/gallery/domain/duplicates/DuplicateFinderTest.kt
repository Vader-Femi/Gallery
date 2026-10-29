package com.femi.gallery.domain.duplicates

import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun item(
    id: Long,
    size: Long,
    type: MediaType = MediaType.Image,
    name: String = "IMG_$id.jpg",
    path: String = "Pictures/",
    taken: Long = 1_000L + id,
    favorite: Boolean = false,
) = MediaItem(
    id = id, uri = "content://media/$id", displayName = name, mimeType = "image/jpeg", type = type,
    dateTakenMillis = taken, dateAddedMillis = taken, width = 100, height = 100, sizeBytes = size,
    durationMillis = 0L, bucketId = 1L, bucketName = "b", relativePath = path, isFavorite = favorite,
)

/** Hashes taken from a table: id -> (pieces, whole). Records which files were read, and how. */
private class FakeHasher(private val table: Map<Long, Pair<String?, String?>>) : FileHasher {
    val partialReads = ArrayList<Long>()
    val fullReads = ArrayList<Long>()

    override suspend fun partialHash(item: MediaItem): String? {
        partialReads.add(item.id)
        return table[item.id]?.first
    }

    override suspend fun fullHash(item: MediaItem): String? {
        fullReads.add(item.id)
        return table[item.id]?.second
    }
}

class DuplicateFinderTest {

    private fun find(items: List<MediaItem>, hasher: FileHasher, progress: (DuplicateProgress) -> Unit = {}) =
        runBlocking { DuplicateFinder(hasher).find(items, progress) }

    @Test
    fun identicalFilesAreGrouped() {
        val items = listOf(item(1, 500), item(2, 500), item(3, 900))
        val groups = find(items, FakeHasher(mapOf(1L to ("p" to "f"), 2L to ("p" to "f"), 3L to ("q" to "g"))))
        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L), groups.single().items.map { it.id }.toSet())
    }

    @Test
    fun filesOfDifferentSizeAreNeverOpened() {
        val hasher = FakeHasher(emptyMap())
        val groups = find(listOf(item(1, 100), item(2, 200), item(3, 300)), hasher)
        assertTrue(groups.isEmpty())
        assertTrue(hasher.partialReads.isEmpty() && hasher.fullReads.isEmpty())
    }

    @Test
    fun aPhotoAndAVideoOfTheSameSizeAreNotCompared() {
        val hasher = FakeHasher(emptyMap())
        val groups = find(listOf(item(1, 500), item(2, 500, type = MediaType.Video)), hasher)
        assertTrue(groups.isEmpty())
        assertTrue(hasher.partialReads.isEmpty())
    }

    @Test
    fun differentPiecesRuleOutAMatchWithoutReadingEverything() {
        val hasher = FakeHasher(mapOf(1L to ("a" to "x"), 2L to ("b" to "x")))
        assertTrue(find(listOf(item(1, 500), item(2, 500)), hasher).isEmpty())
        assertTrue("whole files must not be read when the pieces differ", hasher.fullReads.isEmpty())
    }

    @Test
    fun filesWithMatchingPiecesButDifferentContentAreNotDuplicates() {
        val hasher = FakeHasher(mapOf(1L to ("p" to "x"), 2L to ("p" to "y")))
        assertTrue(find(listOf(item(1, 500), item(2, 500)), hasher).isEmpty())
        assertEquals(listOf(1L, 2L), hasher.fullReads)
    }

    @Test
    fun threeCopiesAndAnUnrelatedPairMakeTwoGroups() {
        val items = listOf(item(1, 500), item(2, 500), item(3, 500), item(4, 800), item(5, 800))
        val table = mapOf(
            1L to ("p" to "f"), 2L to ("p" to "f"), 3L to ("p" to "f"),
            4L to ("q" to "g"), 5L to ("q" to "g"),
        )
        val groups = find(items, FakeHasher(table))
        assertEquals(2, groups.size)
        assertEquals(setOf(3, 2), groups.map { it.items.size }.toSet())
    }

    @Test
    fun unreadableFilesAreLeftOut() {
        val hasher = FakeHasher(mapOf(1L to ("p" to "f"), 2L to (null to null), 3L to ("p" to "f")))
        val groups = find(listOf(item(1, 500), item(2, 500), item(3, 500)), hasher)
        assertEquals(setOf(1L, 3L), groups.single().items.map { it.id }.toSet())
    }

    @Test
    fun emptyFilesAreIgnored() {
        val hasher = FakeHasher(emptyMap())
        assertTrue(find(listOf(item(1, 0), item(2, 0)), hasher).isEmpty())
        assertTrue(hasher.partialReads.isEmpty())
    }

    @Test
    fun biggestSavingsComeFirst() {
        val items = listOf(item(1, 100), item(2, 100), item(3, 9_000), item(4, 9_000))
        val table = mapOf(1L to ("a" to "a"), 2L to ("a" to "a"), 3L to ("b" to "b"), 4L to ("b" to "b"))
        val groups = find(items, FakeHasher(table))
        assertEquals(listOf(9_000L, 100L), groups.map { it.reclaimableBytes })
    }

    @Test
    fun progressCountsFilesInEachPhase() {
        val items = listOf(item(1, 500), item(2, 500), item(3, 700))
        val table = mapOf(1L to ("p" to "f"), 2L to ("p" to "f"), 3L to ("z" to "z"))
        val seen = ArrayList<DuplicateProgress>()
        find(items, FakeHasher(table)) { seen.add(it) }
        assertEquals(
            listOf(
                DuplicateProgress(DuplicatePhase.Sampling, 1, 2),
                DuplicateProgress(DuplicatePhase.Sampling, 2, 2),
                DuplicateProgress(DuplicatePhase.Verifying, 1, 2),
                DuplicateProgress(DuplicatePhase.Verifying, 2, 2),
            ),
            seen,
        )
    }

    @Test
    fun noFilesNoGroups() {
        assertTrue(find(emptyList(), FakeHasher(emptyMap())).isEmpty())
    }
}

class DuplicateKeepRuleTest {

    @Test
    fun aFavoriteIsKept() {
        val keep = DuplicateKeepRule.choose(
            listOf(item(1, 5, path = "DCIM/Camera/", taken = 1), item(2, 5, favorite = true, path = "Download/", taken = 99)),
        )
        assertEquals(2L, keep.id)
    }

    @Test
    fun theCameraOriginalBeatsACopyElsewhere() {
        val keep = DuplicateKeepRule.choose(
            listOf(item(1, 5, path = "Pictures/WhatsApp/", taken = 1), item(2, 5, path = "DCIM/Camera/", taken = 50)),
        )
        assertEquals(2L, keep.id)
    }

    @Test
    fun theEarlierOneIsKeptWhenNothingElseDiffers() {
        val keep = DuplicateKeepRule.choose(listOf(item(1, 5, taken = 200), item(2, 5, taken = 100)))
        assertEquals(2L, keep.id)
    }

    @Test
    fun theShorterNameBeatsACopyName() {
        val keep = DuplicateKeepRule.choose(
            listOf(item(1, 5, name = "IMG_001 (1).jpg", taken = 10), item(2, 5, name = "IMG_001.jpg", taken = 10)),
        )
        assertEquals(2L, keep.id)
    }

    @Test
    fun theAnswerIsStable() {
        val a = item(7, 5, taken = 10, name = "a.jpg")
        val b = item(3, 5, taken = 10, name = "a.jpg")
        assertEquals(3L, DuplicateKeepRule.choose(listOf(a, b)).id)
        assertEquals(3L, DuplicateKeepRule.choose(listOf(b, a)).id)
    }
}

class DuplicateGroupTest {
    private val group = DuplicateGroup(items = listOf(item(1, 100), item(2, 100), item(3, 100)), keepId = 1L)

    @Test
    fun extraCopiesAreEverythingButTheKeptFile() {
        assertEquals(listOf(2L, 3L), group.extraCopies.map { it.id })
        assertEquals(200L, group.reclaimableBytes)
    }

    @Test
    fun anotherFileCanBeKept() {
        val changed = group.withKeep(3L)
        assertEquals(listOf(1L, 2L), changed.extraCopies.map { it.id })
    }

    @Test
    fun keepingAFileThatIsNotInTheGroupChangesNothing() {
        assertEquals(group, group.withKeep(99L))
    }
}
