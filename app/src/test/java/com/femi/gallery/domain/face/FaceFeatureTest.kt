package com.femi.gallery.domain.face

import com.femi.gallery.domain.face.FaceFeatureHelper.toByteArray
import com.femi.gallery.domain.face.FaceFeatureHelper.toFloatArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceFeatureTest {

    @Test
    fun serializationPreservesFloatValues() {
        val original = floatArrayOf(0.123f, -0.456f, 1.0f, 0.0f, 999.9f)
        val bytes = original.toByteArray()
        val restored = bytes.toFloatArray()
        assertArrayEquals(original, restored, 0.00001f)
    }

    @Test
    fun cosineSimilarityOfIdenticalVectorsIsOne() {
        val v = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)
        val sim = FaceFeatureHelper.cosineSimilarity(v, v)
        assertEquals(1.0f, sim, 0.0001f)
    }

    @Test
    fun cosineSimilarityOfOrthogonalVectorsIsZero() {
        val v1 = floatArrayOf(1f, 0f, 0f)
        val v2 = floatArrayOf(0f, 1f, 0f)
        val sim = FaceFeatureHelper.cosineSimilarity(v1, v2)
        assertEquals(0.0f, sim, 0.0001f)
    }

    @Test
    fun cosineSimilarityOfOppositeVectorsIsNegativeOne() {
        val v1 = floatArrayOf(1f, 0f, 0f)
        val v2 = floatArrayOf(-1f, 0f, 0f)
        val sim = FaceFeatureHelper.cosineSimilarity(v1, v2)
        assertEquals(-1.0f, sim, 0.0001f)
    }

    @Test
    fun cosineSimilarityDetectsSimilarFeatures() {
        val base = floatArrayOf(0.2f, 0.8f, 0.1f, 0.5f)
        val slightlyModified = floatArrayOf(0.22f, 0.78f, 0.12f, 0.49f)
        val sim = FaceFeatureHelper.cosineSimilarity(base, slightlyModified)
        assertTrue(sim > 0.98f)
    }

    @Test
    fun zeroCenteredDifferentProfilesHaveLowSimilarity() {
        // Person A has prominent eyebrows and narrow eye-spacing (relative to mean)
        val personA = floatArrayOf(0.8f, -0.6f, 0.4f, -0.5f, 0.1f)
        // Person B has wide eye-spacing and lighter eyebrows (different relative profile)
        val personB = floatArrayOf(-0.7f, 0.5f, -0.4f, 0.6f, -0.2f)
        val sim = FaceFeatureHelper.cosineSimilarity(personA, personB)
        assertTrue(sim < 0.2f)
    }
}
