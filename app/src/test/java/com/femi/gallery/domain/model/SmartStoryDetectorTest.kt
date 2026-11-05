package com.femi.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SmartStoryDetectorTest {

    private val today = LocalDate.of(2026, 11, 5)

    @Test
    fun junkPhotoFilterIdentifiesScreenshotsAndDocuments() {
        assertTrue(
            JunkPhotoFilter.isJunk(
                displayName = "Screenshot_20261027-142201.png",
                relativePath = "Pictures/Screenshots/",
                bucketName = "Screenshots",
            ),
        )
        assertTrue(
            JunkPhotoFilter.isJunk(
                displayName = "Receipt_November.jpg",
                relativePath = "Download/",
                bucketName = "Download",
                labels = listOf("Receipt", "Document", "Paper"),
            ),
        )
        // Camera photo with people or scenic elements is NOT junk
        assertFalse(
            JunkPhotoFilter.isJunk(
                displayName = "IMG_20261105_120000.jpg",
                relativePath = "DCIM/Camera/",
                bucketName = "Camera",
                labels = listOf("Person", "Smile", "Nature"),
                hasFace = true,
            ),
        )
        // Favorites are never junk
        assertFalse(
            JunkPhotoFilter.isJunk(
                displayName = "Screenshot_fav.png",
                relativePath = "Pictures/Screenshots/",
                bucketName = "Screenshots",
                isFavorite = true,
            ),
        )
    }

    @Test
    fun tripStoryGetsPlaceTitleAndSubtitle() {
        val fromDay = LocalDate.of(2026, 10, 1).toEpochDay()
        val toDay = LocalDate.of(2026, 10, 3).toEpochDay()
        val signals = SmartStoryDetector.ContextSignals(
            placeName = "Paris",
            prominentPeople = listOf("Alice"),
        )

        val (title, theme) = SmartStoryDetector.determineTitleAndTheme(fromDay, toDay, signals, today)
        val subtitle = SmartStoryDetector.buildSubtitle(fromDay, toDay, signals, theme, itemCount = 15)

        assertEquals("Trip to Paris", title)
        assertEquals(StoryTheme.Trip, theme)
        assertTrue("Subtitle should contain date and person", subtitle.contains("Oct 1") && subtitle.contains("Alice"))
    }

    @Test
    fun peopleStoryGetsPersonNameTitle() {
        val fromDay = LocalDate.of(2026, 9, 15).toEpochDay()
        val toDay = LocalDate.of(2026, 9, 15).toEpochDay()
        val signals = SmartStoryDetector.ContextSignals(
            prominentPeople = listOf("Sarah", "John"),
        )

        val (title, theme) = SmartStoryDetector.determineTitleAndTheme(fromDay, toDay, signals, today)
        assertEquals("With Sarah & John", title)
        assertEquals(StoryTheme.People, theme)
    }

    @Test
    fun sceneStoryGetsActivityTitle() {
        val fromDay = LocalDate.of(2026, 8, 20).toEpochDay()
        val toDay = LocalDate.of(2026, 8, 20).toEpochDay()
        val signals = SmartStoryDetector.ContextSignals(
            topScene = "beach",
        )

        val (title, theme) = SmartStoryDetector.determineTitleAndTheme(fromDay, toDay, signals, today)
        assertEquals("A Day at the Beach", title)
        assertEquals(StoryTheme.Scene, theme)
    }

    @Test
    fun pastAnniversaryGetsEnrichedTitle() {
        val fromDay = LocalDate.of(2024, 11, 5).toEpochDay() // Exactly 2 years ago today
        val toDay = fromDay
        val signals = SmartStoryDetector.ContextSignals(
            placeName = "Tokyo",
        )

        val (title, theme) = SmartStoryDetector.determineTitleAndTheme(fromDay, toDay, signals, today)
        assertEquals("Tokyo, 2024", title)
        assertEquals(StoryTheme.Anniversary, theme)
    }

    @Test
    fun fallbackOutingsFormatCorrectlyWithoutDateTimePatternCrash() {
        val singleDay = LocalDate.of(2026, 11, 1).toEpochDay() // Sunday
        val (singleTitle, singleTheme) = SmartStoryDetector.determineTitleAndTheme(
            singleDay,
            singleDay,
            SmartStoryDetector.ContextSignals(),
            today,
        )
        assertEquals("Sunday Outing", singleTitle)
        assertEquals(StoryTheme.Outing, singleTheme)

        val multiDayStart = LocalDate.of(2026, 11, 1).toEpochDay()
        val multiDayEnd = LocalDate.of(2026, 11, 3).toEpochDay()
        val (multiTitle, multiTheme) = SmartStoryDetector.determineTitleAndTheme(
            multiDayStart,
            multiDayEnd,
            SmartStoryDetector.ContextSignals(),
            today,
        )
        assertEquals("November Memories", multiTitle)
        assertEquals(StoryTheme.Outing, multiTheme)
    }
}
