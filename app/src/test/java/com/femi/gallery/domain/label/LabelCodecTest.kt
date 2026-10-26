package com.femi.gallery.domain.label

import org.junit.Assert.assertEquals
import org.junit.Test

class LabelCodecTest {

    @Test
    fun testEncodeDecode() {
        val entries = listOf(
            DetectedLabel(1L, 1000L, "dog animal pet"),
            DetectedLabel(2L, 2000L, "car vehicle"),
        )
        val encoded = LabelCodec.encode(entries)
        val decoded = LabelCodec.decode(encoded)

        assertEquals(entries.size, decoded?.size)
        assertEquals(entries[0].id, decoded?.get(0)?.id)
        assertEquals(entries[0].labels, decoded?.get(0)?.labels)
        assertEquals(entries[1].id, decoded?.get(1)?.id)
        assertEquals(entries[1].labels, decoded?.get(1)?.labels)
    }
}
