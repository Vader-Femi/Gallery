package com.femi.gallery.domain.model

import com.femi.gallery.presentation.stories.StoryTitles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

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

        val (titleSpec, theme) = SmartStoryDetector.determineTitleAndTheme(fromDay, toDay, signals, today)
        val subtitleSpec = SmartStoryDetector.buildSubtitle(fromDay, toDay, signals, theme, itemCount = 15)

        assertEquals(StoryTitleSpec.Trip("Paris", isSingleDay = false), titleSpec)
        assertEquals(StoryTheme.Trip, theme)
        assertEquals(StorySubtitleSpec(fromDay = fromDay, toDay = toDay, withPerson = "Alice"), subtitleSpec)

        assertEquals("Trip to Paris", StoryTitles.formatTitle(titleSpec))
        val formattedSubtitle = StoryTitles.formatSubtitle(subtitleSpec, locale = Locale.US)
        assertTrue("Subtitle should contain date and person", formattedSubtitle.contains("Oct 1") && formattedSubtitle.contains("Alice"))
    }

    @Test
    fun peopleStoryGetsPersonNameTitle() {
        val fromDay = LocalDate.of(2026, 9, 15).toEpochDay()
        val toDay = LocalDate.of(2026, 9, 15).toEpochDay()
        val signals = SmartStoryDetector.ContextSignals(
            prominentPeople = listOf("Sarah", "John"),
        )

        val (titleSpec, theme) = SmartStoryDetector.determineTitleAndTheme(fromDay, toDay, signals, today)
        assertEquals(StoryTitleSpec.WithPerson(listOf("Sarah", "John")), titleSpec)
        assertEquals(StoryTheme.People, theme)
        assertEquals("With Sarah & John", StoryTitles.formatTitle(titleSpec))
    }

    @Test
    fun sceneStoryGetsActivityTitle() {
        val fromDay = LocalDate.of(2026, 8, 20).toEpochDay()
        val toDay = LocalDate.of(2026, 8, 20).toEpochDay()
        val signals = SmartStoryDetector.ContextSignals(
            topScene = "beach",
        )

        val (titleSpec, theme) = SmartStoryDetector.determineTitleAndTheme(fromDay, toDay, signals, today)
        assertEquals(StoryTitleSpec.Scene(StoryScene.Beach), titleSpec)
        assertEquals(StoryTheme.Scene, theme)
        assertEquals("A Day at the Beach", StoryTitles.formatScene(StoryScene.Beach, null))
        assertEquals("A Day at the Beach", StoryTitles.formatTitle(titleSpec))
    }

    @Test
    fun pastAnniversaryGetsEnrichedTitle() {
        val fromDay = LocalDate.of(2024, 11, 5).toEpochDay() // Exactly 2 years ago today
        val toDay = fromDay
        val signals = SmartStoryDetector.ContextSignals(
            placeName = "Tokyo",
        )

        val (titleSpec, theme) = SmartStoryDetector.determineTitleAndTheme(fromDay, toDay, signals, today)
        assertEquals(StoryTitleSpec.Anniversary(year = 2024, place = "Tokyo"), titleSpec)
        assertEquals(StoryTheme.Anniversary, theme)
        assertEquals("Tokyo, 2024", StoryTitles.formatTitle(titleSpec))
    }

    @Test
    fun fallbackOutingsFormatCorrectlyWithoutDateTimePatternCrash() {
        val singleDay = LocalDate.of(2026, 11, 1).toEpochDay() // Sunday
        val (singleTitleSpec, singleTheme) = SmartStoryDetector.determineTitleAndTheme(
            singleDay,
            singleDay,
            SmartStoryDetector.ContextSignals(),
            today,
        )
        assertEquals(StoryTitleSpec.WeekdayOuting(7), singleTitleSpec)
        assertEquals(StoryTheme.Outing, singleTheme)
        assertEquals("Sunday Outing", StoryTitles.formatTitle(singleTitleSpec, locale = Locale.US))

        val multiDayStart = LocalDate.of(2026, 11, 1).toEpochDay()
        val multiDayEnd = LocalDate.of(2026, 11, 3).toEpochDay()
        val (multiTitleSpec, multiTheme) = SmartStoryDetector.determineTitleAndTheme(
            multiDayStart,
            multiDayEnd,
            SmartStoryDetector.ContextSignals(),
            today,
        )
        assertEquals(StoryTitleSpec.MonthMemories(month = 11, year = 2026), multiTitleSpec)
        assertEquals(StoryTheme.Outing, multiTheme)
        assertEquals("November Memories", StoryTitles.formatTitle(multiTitleSpec, locale = Locale.US))
    }
}
