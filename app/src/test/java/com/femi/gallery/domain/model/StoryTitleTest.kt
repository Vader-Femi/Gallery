package com.femi.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryTitleTest {
    @Test
    fun spacesAreTidied() {
        assertEquals("Beach day", StoryTitle.clean("  Beach    day \n"))
    }

    @Test
    fun blankIsNothing() {
        assertNull(StoryTitle.clean(""))
        assertNull(StoryTitle.clean("   "))
    }

    @Test
    fun longTitlesAreCut() {
        val cleaned = StoryTitle.clean("a".repeat(200))
        assertEquals(StoryTitle.MAX_LENGTH, cleaned?.length)
    }
}
