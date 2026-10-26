package com.femi.gallery.domain.color

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorCodecTest {

    @Test
    fun testEncodeDecode() {
        val entries = listOf(
            DetectedColor(1L, 1000L, "blue dark"),
            DetectedColor(2L, 2000L, "red light"),
        )
        val encoded = ColorCodec.encode(entries)
        val decoded = ColorCodec.decode(encoded)

        assertEquals(entries.size, decoded?.size)
        assertEquals(entries[0].id, decoded?.get(0)?.id)
        assertEquals(entries[0].colors, decoded?.get(0)?.colors)
        assertEquals(entries[1].id, decoded?.get(1)?.id)
        assertEquals(entries[1].colors, decoded?.get(1)?.colors)
    }
}
