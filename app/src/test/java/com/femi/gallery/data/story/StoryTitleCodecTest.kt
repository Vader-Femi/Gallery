package com.femi.gallery.data.story

import com.femi.gallery.domain.model.StoryScene
import com.femi.gallery.domain.model.StorySubtitleSpec
import com.femi.gallery.domain.model.StoryTitleSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryTitleCodecTest {

    @Test
    fun roundTripTitleSpecs() {
        val specs = listOf(
            StoryTitleSpec.Custom("My Custom Title"),
            StoryTitleSpec.Trip("Tokyo", isSingleDay = false),
            StoryTitleSpec.WithPerson(listOf("Alice", "Bob")),
            StoryTitleSpec.Scene(StoryScene.Beach),
            StoryTitleSpec.Scene(StoryScene.Other("Museum")),
            StoryTitleSpec.Anniversary(2024, place = "Paris"),
            StoryTitleSpec.Anniversary(2023, person = "Alice"),
            StoryTitleSpec.Anniversary(2022, scene = StoryScene.GoldenHour),
            StoryTitleSpec.WeekdayOuting(1),
            StoryTitleSpec.MonthMemories(month = 10, year = 2026),
            StoryTitleSpec.DateRange(100L, 105L),
        )

        for (spec in specs) {
            val encoded = StoryTitleCodec.encodeTitle(spec)
            assertNotNull("Encoded string should not be null for $spec", encoded)
            val decoded = StoryTitleCodec.decodeTitle(encoded)
            assertEquals("Decoded spec should match original for $spec", spec, decoded)
        }
    }

    @Test
    fun roundTripSubtitleSpec() {
        val spec = StorySubtitleSpec(
            fromDay = 1000L,
            toDay = 1005L,
            withPerson = "John",
            placeName = "London",
        )
        val encoded = StoryTitleCodec.encodeSubtitle(spec)
        assertNotNull(encoded)
        val decoded = StoryTitleCodec.decodeSubtitle(encoded)
        assertEquals(spec, decoded)
    }

    @Test
    fun legacyStringFallback() {
        val legacy = "Trip to Paris"
        val decoded = StoryTitleCodec.decodeTitle(legacy)
        assertTrue(decoded is StoryTitleSpec.Custom)
        assertEquals(legacy, (decoded as StoryTitleSpec.Custom).text)
    }

    @Test
    fun nullHandling() {
        assertNull(StoryTitleCodec.encodeTitle(null))
        assertNull(StoryTitleCodec.decodeTitle(null))
        assertNull(StoryTitleCodec.decodeTitle(""))
        assertNull(StoryTitleCodec.encodeSubtitle(null))
        assertNull(StoryTitleCodec.decodeSubtitle(null))
    }
}
