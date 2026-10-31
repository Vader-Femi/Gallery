package com.femi.gallery.domain.clip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun emb(id: Long, vararg x: Float, modified: Long = 1L) = DetectedEmbedding(id, modified, ClipQuantizer.quantize(floatArrayOf(*x)))

class ClipQuantizerTest {

    @Test
    fun normalizeGivesLengthOne() {
        val unit = ClipQuantizer.normalize(floatArrayOf(3f, 4f))
        assertEquals(0.6f, unit[0], 1e-6f)
        assertEquals(0.8f, unit[1], 1e-6f)
    }

    @Test
    fun aVectorOfZerosStaysZeros() {
        assertTrue(ClipQuantizer.normalize(floatArrayOf(0f, 0f)).all { it == 0f })
        assertEquals(0, ClipQuantizer.quantize(floatArrayOf(0f, 0f)).size)
        assertEquals(0, ClipQuantizer.quantize(FloatArray(0)).size)
    }

    @Test
    fun keepingAsBytesLosesLittle() {
        val random = java.util.Random(7)
        val v = FloatArray(512) { random.nextGaussian().toFloat() }
        val unit = ClipQuantizer.normalize(v)
        val bytes = ClipQuantizer.quantize(v)
        assertEquals(512, bytes.size)
        assertTrue("worst difference ${ClipQuantizer.maxDifference(unit, bytes)}", ClipQuantizer.maxDifference(unit, bytes) < 0.005f)
    }

    @Test
    fun cosineOfAVectorWithItselfIsAboutOne() {
        val v = floatArrayOf(0.3f, -0.5f, 0.8f, 0.1f)
        val unit = ClipQuantizer.normalize(v)
        assertEquals(1f, ClipQuantizer.cosine(unit, ClipQuantizer.quantize(v), 0), 0.01f)
    }

    @Test
    fun cosineOfOppositeVectorsIsAboutMinusOne() {
        val v = floatArrayOf(0.3f, -0.5f, 0.8f)
        val opposite = floatArrayOf(-0.3f, 0.5f, -0.8f)
        assertEquals(-1f, ClipQuantizer.cosine(ClipQuantizer.normalize(v), ClipQuantizer.quantize(opposite), 0), 0.01f)
    }

    @Test
    fun cosineAtAnOffsetReadsTheRightVector() {
        val a = ClipQuantizer.quantize(floatArrayOf(1f, 0f))
        val b = ClipQuantizer.quantize(floatArrayOf(0f, 1f))
        val data = a + b
        val q = floatArrayOf(0f, 1f)
        assertEquals(0f, ClipQuantizer.cosine(q, data, 0), 0.01f)
        assertEquals(1f, ClipQuantizer.cosine(q, data, 2), 0.01f)
    }
}

class ClipIndexTest {

    @Test
    fun theClosestPhotoIsFound() {
        val index = ClipIndex(listOf(emb(1, 1f, 0f, 0f), emb(2, 0f, 1f, 0f)))
        val found = index.search(floatArrayOf(0.9f, 0.1f, 0f), SemanticSelection.Config(minScore = 0.5f, margin = 0.1f))
        assertEquals(listOf(1L), found)
    }

    @Test
    fun scoresFollowTheOrderOfIdsInOrder() {
        val index = ClipIndex(listOf(emb(5, 1f, 0f), emb(9, 0f, 1f)))
        assertEquals(listOf(5L, 9L), index.idsInOrder().toList())
        val scores = index.scores(floatArrayOf(1f, 0f))!!
        assertTrue(scores[0] > 0.9f && scores[1] < 0.1f)
    }

    @Test
    fun unreadablePhotosAreCountedAsReadButNotCompared() {
        val index = ClipIndex(listOf(emb(1, 1f, 0f), DetectedEmbedding(2, 1, ByteArray(0))))
        assertEquals(2, index.readCount)
        assertEquals(1, index.embeddedCount)
    }

    @Test
    fun embeddingsOfAnotherSizeAreLeftOut() {
        val index = ClipIndex(listOf(emb(1, 1f, 0f), emb(2, 1f, 0f, 0f)))
        assertEquals(2, index.dimension)
        assertEquals(listOf(1L), index.idsInOrder().toList())
    }

    @Test
    fun aQueryOfAnotherSizeFindsNothing() {
        val index = ClipIndex(listOf(emb(1, 1f, 0f)))
        assertNull(index.scores(floatArrayOf(1f, 0f, 0f)))
        assertTrue(index.search(floatArrayOf(1f, 0f, 0f)).isEmpty())
    }

    @Test
    fun anEmptyIndexFindsNothing() {
        assertTrue(ClipIndex.Empty.search(floatArrayOf(1f)).isEmpty())
        assertEquals(0, ClipIndex.Empty.dimension)
    }

    @Test
    fun embeddingsWithTheSameBytesAreEqual() {
        assertEquals(emb(1, 1f, 2f), emb(1, 1f, 2f))
    }
}

class SemanticSelectionTest {
    private val ids = longArrayOf(10, 11, 12, 13, 14)

    @Test
    fun onlyPhotosCloseToTheBestAreKept() {
        val scores = floatArrayOf(0.30f, 0.28f, 0.22f, 0.15f, 0.29f)
        val kept = SemanticSelection.select(ids, scores, SemanticSelection.Config(minScore = 0.2f, margin = 0.05f))
        assertEquals(listOf(10L, 14L, 11L), kept) // closest first; 0.22 is more than 0.05 behind 0.30
    }

    @Test
    fun nothingMatchesWhenEvenTheBestIsBelowTheFloor() {
        val scores = floatArrayOf(0.18f, 0.12f, 0.10f, 0.15f, 0.11f)
        assertTrue(SemanticSelection.select(ids, scores).isEmpty())
    }

    @Test
    fun theFloorAppliesEvenWithinTheMargin() {
        val scores = floatArrayOf(0.22f, 0.19f, 0.10f, 0.10f, 0.10f)
        val kept = SemanticSelection.select(ids, scores, SemanticSelection.Config(minScore = 0.2f, margin = 0.1f))
        assertEquals(listOf(10L), kept)
    }

    @Test
    fun atMostTheMaximumAreKept() {
        val many = LongArray(10) { it.toLong() }
        val scores = FloatArray(10) { 0.3f - it * 0.001f }
        val kept = SemanticSelection.select(many, scores, SemanticSelection.Config(minScore = 0.2f, margin = 0.1f, maxResults = 3))
        assertEquals(listOf(0L, 1L, 2L), kept)
    }

    @Test
    fun mismatchedOrEmptyInputFindsNothing() {
        assertTrue(SemanticSelection.select(LongArray(0), FloatArray(0)).isEmpty())
        assertTrue(SemanticSelection.select(ids, floatArrayOf(0.3f)).isEmpty())
    }
}

class ClipPreprocessTest {

    @Test
    fun theShorterSideBecomesExactlyTheSize() {
        val (w, h) = ClipPreprocess.scaledSize(4000, 3000)
        assertEquals(224, h)
        assertTrue(w in 298..299)
        val (pw, ph) = ClipPreprocess.scaledSize(3000, 4000)
        assertEquals(224, pw)
        assertTrue(ph in 298..299)
    }

    @Test
    fun aSquarePhotoIsJustScaled() {
        assertEquals(224 to 224, ClipPreprocess.scaledSize(1000, 1000))
    }

    @Test
    fun aTinyPhotoIsScaledUp() {
        assertEquals(224 to 224, ClipPreprocess.scaledSize(56, 56))
    }

    @Test
    fun theCropIsInTheMiddle() {
        assertEquals(37 to 0, ClipPreprocess.cropOrigin(298, 224))
        assertEquals(0 to 37, ClipPreprocess.cropOrigin(224, 298))
        assertEquals(0 to 0, ClipPreprocess.cropOrigin(224, 224))
    }

    @Test
    fun theTensorHasThreePlanesInRedGreenBlueOrder() {
        // One pixel colour per plane value check on a 2 x 2 picture.
        val red = 0xFFFF0000.toInt()
        val tensor = ClipPreprocess.toTensor(intArrayOf(red, red, red, red), size = 2)
        assertEquals(12, tensor.size)
        val expectedRed = (1f - ClipPreprocess.MEAN[0]) / ClipPreprocess.STD[0]
        val expectedGreen = (0f - ClipPreprocess.MEAN[1]) / ClipPreprocess.STD[1]
        val expectedBlue = (0f - ClipPreprocess.MEAN[2]) / ClipPreprocess.STD[2]
        for (i in 0 until 4) {
            assertEquals(expectedRed, tensor[i], 1e-5f)
            assertEquals(expectedGreen, tensor[4 + i], 1e-5f)
            assertEquals(expectedBlue, tensor[8 + i], 1e-5f)
        }
    }

    @Test
    fun eachPixelGoesToItsOwnPosition() {
        val pixels = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        val tensor = ClipPreprocess.toTensor(pixels, size = 2)
        assertTrue(tensor[0] < tensor[1]) // black before white in the red plane
        assertTrue(tensor[2] < tensor[3])
    }
}
