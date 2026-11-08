package com.femi.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HiddenAlbumsKeyTest {
    @Test
    fun noHiddenAlbumIsZero() {
        assertEquals(0L, HiddenAlbumsKey.of(emptySet()))
    }

    @Test
    fun theOrderDoesNotMatter() {
        assertEquals(HiddenAlbumsKey.of(listOf(5L, 9L, 12L)), HiddenAlbumsKey.of(listOf(12L, 5L, 9L)))
        assertEquals(HiddenAlbumsKey.of(listOf(5L, 5L, 9L)), HiddenAlbumsKey.of(setOf(9L, 5L)))
    }

    @Test
    fun anotherSetGivesAnotherNumber() {
        val keys = listOf(setOf(1L), setOf(2L), setOf(1L, 2L), setOf(1L, 3L), setOf(-1L), setOf(Long.MAX_VALUE), setOf(0L))
            .map { HiddenAlbumsKey.of(it) }
        assertEquals(keys.size, keys.toSet().size)
        assertNotEquals(0L, HiddenAlbumsKey.of(setOf(0L)))
    }
}
