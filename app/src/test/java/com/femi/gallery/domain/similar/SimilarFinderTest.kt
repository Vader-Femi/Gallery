package com.femi.gallery.domain.similar

import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun photo(
    id: Long,
    size: Long = 1_000L,
    width: Int = 100,
    height: Int = 100,
    taken: Long = 1_000L + id,
    path: String = "Pictures/",
    favorite: Boolean = false,
) = MediaItem(
    id = id, uri = "content://media/$id", displayName = "IMG_$id.jpg", mimeType = "image/jpeg", type = MediaType.Image,
    dateTakenMillis = taken, dateAddedMillis = taken, width = width, height = height, sizeBytes = size,
    durationMillis = 0L, bucketId = id % 3, bucketName = "b", relativePath = path, isFavorite = favorite,
)

/** [base] with the bits at [positions] (0 to 63) flipped. */
private fun flip(base: Long, vararg positions: Int): Long = positions.fold(base) { h, p -> h xor (1L shl p) }

class SimilarFinderTest {
    private val base = 0x5A3C_96E1_0F2B_4D78L
    private val finder = SimilarFinder(maxDistance = 6)

    @Test
    fun photosWithNearlyTheSameHashAreOneGroup() {
        val items = listOf(photo(1), photo(2), photo(3))
        val hashes = mapOf(1L to base, 2L to flip(base, 0, 20, 41), 3L to flip(base, 63))
        val groups = finder.find(items, hashes)
        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L, 3L), groups[0].items.map { it.id }.toSet())
    }

    @Test
    fun photosFartherApartThanTheLimitAreNot() {
        val items = listOf(photo(1), photo(2))
        val seven = mapOf(1L to base, 2L to flip(base, 0, 9, 18, 27, 36, 45, 54))
        assertTrue(finder.find(items, seven).isEmpty())
        val six = mapOf(1L to base, 2L to flip(base, 0, 9, 18, 27, 36, 45))
        assertEquals(1, finder.find(items, six).size)
    }

    @Test
    fun theLimitHoldsWhereverTheDifferingBitsAre() {
        // A difference of exactly the limit must be found whichever pieces of the hash the bits fall in.
        for (start in 0..57) {
            val bits = IntArray(6) { start + it }.filter { it < 64 }.toIntArray()
            val groups = finder.find(listOf(photo(1), photo(2)), mapOf(1L to base, 2L to flip(base, *bits)))
            assertEquals("bits from $start", 1, groups.size)
        }
        val spread = intArrayOf(1, 11, 22, 33, 44, 55)
        assertEquals(1, finder.find(listOf(photo(1), photo(2)), mapOf(1L to base, 2L to flip(base, *spread))).size)
    }

    @Test
    fun aChainOfNeighboursDoesNotPullUnlikePhotosTogether() {
        // 1 is like 2, 2 is like 3, ... but 1 and 5 are far apart: they must not end up in one group.
        val items = (1L..5L).map { photo(it) }
        var h = base
        val hashes = HashMap<Long, Long>()
        hashes[1] = h
        for (id in 2L..5L) {
            h = flip(h, ((id - 2) * 4).toInt(), ((id - 2) * 4 + 1).toInt(), ((id - 2) * 4 + 2).toInt(), ((id - 2) * 4 + 3).toInt())
            hashes[id] = h
        }
        val groups = finder.find(items, hashes)
        for (g in groups) {
            val first = hashes.getValue(g.items.first().id)
            assertTrue(g.items.all { DifferenceHash.distance(first, hashes.getValue(it.id)) <= 6 })
        }
        assertTrue(groups.none { it.items.size == 5 })
    }

    @Test
    fun photosWithoutAHashAndLoneHashesAreLeftOut() {
        val items = listOf(photo(1), photo(2), photo(3))
        val groups = finder.find(items, mapOf(1L to base, 2L to base))
        assertEquals(setOf(1L, 2L), groups.single().items.map { it.id }.toSet())
        assertTrue(finder.find(items, mapOf(1L to base)).isEmpty())
        assertTrue(finder.find(emptyList(), emptyMap()).isEmpty())
    }

    @Test
    fun theKeptPhotoIsTheBestOne() {
        val items = listOf(photo(1, width = 100, height = 100), photo(2, width = 400, height = 300), photo(3, width = 400, height = 300, size = 5_000))
        val groups = finder.find(items, items.associate { it.id to base })
        assertEquals(3L, groups.single().keepId)
        assertEquals(setOf(1L, 2L), groups.single().extraCopies.map { it.id }.toSet())
    }

    @Test
    fun aFavoriteIsKeptEvenWhenSmaller() {
        val items = listOf(photo(1, width = 100, height = 100, favorite = true), photo(2, width = 800, height = 600))
        assertEquals(1L, finder.find(items, items.associate { it.id to base }).single().keepId)
    }

    @Test
    fun cameraFolderBreaksATieAndTheEarlierPhotoAfterThat() {
        val items = listOf(photo(1, path = "Pictures/", taken = 10), photo(2, path = "DCIM/Camera/", taken = 20), photo(3, path = "DCIM/Camera/", taken = 5))
        assertEquals(3L, finder.find(items, items.associate { it.id to base }).single().keepId)
    }

    @Test
    fun groupsWithTheMostToGainComeFirst() {
        val small = listOf(photo(1, size = 100), photo(2, size = 100))
        val big = listOf(photo(3, size = 9_000), photo(4, size = 9_000))
        val other = flip(base, 2, 5, 9, 14, 20, 25, 31, 37, 44, 50, 52, 58) // far from base
        val hashes = mapOf(1L to base, 2L to base, 3L to other, 4L to other)
        val groups = finder.find(small + big, hashes)
        assertEquals(2, groups.size)
        assertEquals(setOf(3L, 4L), groups[0].items.map { it.id }.toSet())
    }

    @Test
    fun theSameInputGivesTheSameGroups() {
        val items = (1L..30L).map { photo(it) }
        val hashes = items.associate { it.id to flip(base, (it.id % 5).toInt(), ((it.id * 7) % 64).toInt()) }
        assertEquals(finder.find(items, hashes), finder.find(items.reversed(), hashes))
    }
}
