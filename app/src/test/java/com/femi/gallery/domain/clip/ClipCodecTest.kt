package com.femi.gallery.domain.clip

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipCodecTest {

    @Test
    fun testEncodeDecode() {
        val entries = listOf(
            DetectedEmbedding(1L, 1000L, floatArrayOf(0.1f, 0.2f, 0.3f)),
            DetectedEmbedding(2L, 2000L, floatArrayOf(0.4f, 0.5f, 0.6f)),
        )
        val encoded = ClipCodec.encode(entries)
        val decoded = ClipCodec.decode(encoded)

        assertEquals(entries.size, decoded?.size)
        assertEquals(entries[0].id, decoded?.get(0)?.id)
        assertArrayEquals(entries[0].embedding, decoded?.get(0)?.embedding!!, 1e-5f)
        assertEquals(entries[1].id, decoded?.get(1)?.id)
        assertArrayEquals(entries[1].embedding, decoded?.get(1)?.embedding!!, 1e-5f)
    }
}
