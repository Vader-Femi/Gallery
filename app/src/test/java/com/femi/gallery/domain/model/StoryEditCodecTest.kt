package com.femi.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryEditCodecTest {

    @Test
    fun roundTrip() {
        val edits = listOf(
            StoryEdit(100, 103, "Lagos trip", 42L),
            StoryEdit(500, 500, null, 7L),
            StoryEdit(600, 601, "Ọjọ́ ìbí 🎂", null),
        )
        assertEquals(edits, StoryEditCodec.decode(StoryEditCodec.encode(edits)))
    }

    @Test
    fun emptyListRoundTrips() {
        assertEquals(emptyList<StoryEdit>(), StoryEditCodec.decode(StoryEditCodec.encode(emptyList())))
    }

    @Test
    fun garbageIsRejected() {
        assertNull(StoryEditCodec.decode(byteArrayOf(1, 2, 3)))
        assertNull(StoryEditCodec.decode(ByteArray(0)))
    }

    @Test
    fun aCutOffFileIsRejected() {
        val bytes = StoryEditCodec.encode(listOf(StoryEdit(1, 2, "abc", 3L)))
        assertNull(StoryEditCodec.decode(bytes.copyOf(bytes.size - 3)))
    }
}
