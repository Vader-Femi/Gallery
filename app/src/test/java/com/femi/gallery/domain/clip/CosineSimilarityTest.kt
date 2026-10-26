package com.femi.gallery.domain.clip

import org.junit.Assert.assertEquals
import org.junit.Test

class CosineSimilarityTest {

    @Test
    fun testIdenticalVectors() {
        val v = floatArrayOf(1f, 2f, 3f)
        val score = CosineSimilarity.compute(v, v)
        assertEquals(1f, score, 1e-5f)
    }

    @Test
    fun testOrthogonalVectors() {
        val v1 = floatArrayOf(1f, 0f)
        val v2 = floatArrayOf(0f, 1f)
        val score = CosineSimilarity.compute(v1, v2)
        assertEquals(0f, score, 1e-5f)
    }
}
