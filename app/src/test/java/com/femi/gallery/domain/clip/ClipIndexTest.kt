package com.femi.gallery.domain.clip

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipIndexTest {

    @Test
    fun testMatchingIds() {
        val index = ClipIndex(
            listOf(
                DetectedEmbedding(1L, 1000L, floatArrayOf(1f, 0f, 0f)),
                DetectedEmbedding(2L, 2000L, floatArrayOf(0f, 1f, 0f)),
            )
        )
        val matched = index.idsMatching(floatArrayOf(0.9f, 0.1f, 0f), threshold = 0.5f)
        assertEquals(setOf(1L), matched)
    }
}
