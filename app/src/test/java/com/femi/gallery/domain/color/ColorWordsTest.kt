package com.femi.gallery.domain.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorWordsTest {
    @Test
    fun theClassifiersOwnTagsMapToThemselves() {
        assertEquals("red", ColorWords.tagFor("red"))
        assertEquals("grey", ColorWords.tagFor("Grey"))
    }

    @Test
    fun otherColourWordsMapToATag() {
        assertEquals("blue", ColorWords.tagFor("navy"))
        assertEquals("purple", ColorWords.tagFor("violet"))
        assertEquals("brown", ColorWords.tagFor("beige"))
        assertEquals("dark", ColorWords.tagFor("night"))
    }

    @Test
    fun otherWordsAreNotColours() {
        assertNull(ColorWords.tagFor("dog"))
        assertNull(ColorWords.tagFor(""))
    }
}
