package com.femi.gallery.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTest {

    // ---- normalizing

    @Test
    fun normalizingIgnoresCaseAccentsAndPunctuation() {
        assertEquals("cafe menu no 4", TextNormalizer.normalize("Café-Menu\nNº 4"))
        assertEquals("final 12 m2", TextNormalizer.normalize("ﬁnal 12 m²"))
        assertEquals("invoice no 2024 1138", TextNormalizer.normalize("  INVOICE  No.\t2024/1138 "))
        assertEquals("", TextNormalizer.normalize(" ... \n"))
        assertEquals("zurich", TextNormalizer.normalize("Zürich"))
    }

    // ---- searching

    private val index = TextIndex(
        listOf(
            DetectedText(1, 10, "INVOICE No. 2024-1138\nTotal: ₦45,000"),
            DetectedText(2, 10, "Welcome to the café"),
            DetectedText(3, 10, ""), // read, no text
            DetectedText(4, 10, "Boarding pass LOS → ABV\nGate 14"),
            DetectedText(5, 10, "Start of the artwork"),
        ),
    )

    @Test
    fun aWordFindsPhotosWhoseTextContainsIt() {
        assertEquals(setOf(1L), index.idsMatching("invoice"))
        assertEquals(setOf(1L), index.idsMatching("INVOICE"))
        assertEquals(setOf(2L), index.idsMatching("cafe")) // accents do not matter
        assertEquals(setOf(2L), index.idsMatching("café"))
    }

    @Test
    fun partOfAWordIsEnoughFromThreeCharacters() {
        assertEquals(setOf(1L), index.idsMatching("invoi"))
        assertEquals(setOf(4L), index.idsMatching("board"))
    }

    @Test
    fun numbersAndSeparatedWordsWork() {
        assertEquals(setOf(1L), index.idsMatching("2024"))
        assertEquals(setOf(1L), index.idsMatching("2024-1138"))
        assertEquals(setOf(4L), index.idsMatching("gate 14"))
    }

    @Test
    fun veryShortWordsOnlyMatchWholeWords() {
        assertEquals(setOf(5L), index.idsMatching("of")) // "of", not the "of" inside other words
        assertEquals(emptySet<Long>(), index.idsMatching("ar")) // inside "artwork" and "start": not a word
        assertEquals(setOf(4L), index.idsMatching("14"))
    }

    @Test
    fun nothingMatchesAnEmptyTermOrAnUnknownWord() {
        assertEquals(emptySet<Long>(), index.idsMatching(""))
        assertEquals(emptySet<Long>(), index.idsMatching(" - "))
        assertEquals(emptySet<Long>(), index.idsMatching("giraffe"))
        assertEquals(emptySet<Long>(), TextIndex.Empty.idsMatching("invoice"))
    }

    @Test
    fun countsReadPhotosAndPhotosWithText() {
        assertEquals(5, index.readCount)
        assertEquals(4, index.withTextCount)
    }

    // ---- file format

    private val entries = listOf(
        DetectedText(28_388L, 1_760_000_000L, "Zürich — Café 北京 ₦45,000"),
        DetectedText(5L, 1_700_000_000L, ""),
        DetectedText(Long.MAX_VALUE, 0L, "line one\nline two"),
    )

    @Test
    fun theFileFormatRoundTripsIncludingAccentsAndEmptyText() {
        assertEquals(entries, TextCodec.decode(TextCodec.encode(entries)))
        assertEquals(emptyList<DetectedText>(), TextCodec.decode(TextCodec.encode(emptyList())))
    }

    @Test
    fun aDamagedFileDecodesToNull() {
        val good = TextCodec.encode(entries)
        assertNotNull(TextCodec.decode(good))
        assertNull(TextCodec.decode(ByteArray(0)))
        assertNull(TextCodec.decode(good.copyOf(good.size - 3))) // cut short
        assertNull(TextCodec.decode(good + byteArrayOf(0))) // trailing bytes
        assertNull(TextCodec.decode(good.also { it[0] = 0 }.copyOf())) // wrong magic
        val bigCount = TextCodec.encode(entries).also { it[8] = 0x7F }
        assertNull(TextCodec.decode(bigCount))
    }

    // ---- what to scan

    @Test
    fun aFirstScanReadsEverythingNewestChangeFirst() {
        val plan = StampPlanner.plan(LongArray(0), LongArray(0), longArrayOf(1, 2, 3), longArrayOf(100, 300, 200))
        assertEquals(emptySet<Long>(), plan.keepIds)
        assertEquals(listOf(2L, 3L, 1L), plan.scanIds.toList())
        assertEquals(listOf(300L, 200L, 100L), plan.scanModified.toList())
    }

    @Test
    fun unchangedPhotosAreKeptChangedAndNewOnesAreRead() {
        val plan = StampPlanner.plan(
            cachedIds = longArrayOf(1, 2, 9), // 9 was deleted
            cachedModified = longArrayOf(100, 200, 900),
            ids = longArrayOf(1, 2, 3),
            modified = longArrayOf(100, 250, 300), // 2 changed, 3 is new
        )
        assertEquals(setOf(1L), plan.keepIds)
        assertEquals(listOf(3L, 2L), plan.scanIds.toList())
        assertEquals(2, plan.toScan)
    }

    @Test
    fun anInterruptedScanResumesWithTheRest() {
        val plan = StampPlanner.plan(longArrayOf(3, 2), longArrayOf(30, 20), longArrayOf(1, 2, 3, 4), longArrayOf(10, 20, 30, 40))
        assertEquals(setOf(2L, 3L), plan.keepIds)
        assertEquals(listOf(4L, 1L), plan.scanIds.toList())
    }

    @Test
    fun everyPhotoEndsUpKeptOrScannedExactlyOnce() {
        val ids = LongArray(1_000) { it.toLong() }
        val modified = LongArray(1_000) { (it * 7 % 13).toLong() }
        val cachedIds = LongArray(334) { it * 3L }
        val cachedModified = LongArray(334) { modified[it * 3] }
        val plan = StampPlanner.plan(cachedIds, cachedModified, ids, modified)
        assertEquals(1_000, plan.keepIds.size + plan.toScan)
        assertTrue(plan.keepIds.none { it in plan.scanIds.toSet() })
    }
}
