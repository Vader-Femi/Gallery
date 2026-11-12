package com.femi.gallery.presentation.stories

import com.femi.gallery.domain.model.StoryScene
import com.femi.gallery.domain.model.StorySubtitleSpec
import com.femi.gallery.domain.model.StoryTitleSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class StoryTitlesTest {

    private val today = LocalDate.of(2026, 10, 18)

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    private fun title(from: Long, to: Long) = StoryTitles.range(from, to, today, Locale.US)

    @Test
    fun singleDayThisYear() {
        assertEquals("Sat, Oct 3", title(day(2026, 10, 3), day(2026, 10, 3)))
    }

    @Test
    fun onThisDayPastYear() {
        assertEquals("On this day in 2024", title(day(2024, 10, 18), day(2024, 10, 18)))
    }

    @Test
    fun singleDayInAnotherYearShowsTheYear() {
        assertEquals("Dec 31, 2025", title(day(2025, 12, 31), day(2025, 12, 31)))
    }

    @Test
    fun severalDaysInOneMonth() {
        assertEquals("Oct 3 – 5", title(day(2026, 10, 3), day(2026, 10, 5)))
    }

    @Test
    fun severalDaysAcrossMonths() {
        assertEquals("Oct 30 – Nov 2", title(day(2026, 10, 30), day(2026, 11, 2)))
    }

    @Test
    fun previousYearGetsAYearSuffix() {
        assertEquals("Mar 3 – 5, 2025", title(day(2025, 3, 3), day(2025, 3, 5)))
    }

    @Test
    fun acrossNewYear() {
        assertEquals("Dec 30, 2025 – Jan 2, 2026", title(day(2025, 12, 30), day(2026, 1, 2)))
    }

    @Test
    fun formatsAllStructuredStoryTitleSpecs() {
        val custom = StoryTitleSpec.Custom("Family Trip")
        assertEquals("Family Trip", StoryTitles.formatTitle(custom))

        val annivPlace = StoryTitleSpec.Anniversary(2023, place = "London")
        assertEquals("London, 2023", StoryTitles.formatTitle(annivPlace))

        val annivPerson = StoryTitleSpec.Anniversary(2022, person = "Mom")
        assertEquals("With Mom, 2022", StoryTitles.formatTitle(annivPerson))

        val annivScene = StoryTitleSpec.Anniversary(2021, scene = StoryScene.Beach)
        assertEquals("A Day at the Beach, 2021", StoryTitles.formatTitle(annivScene))

        val annivDefault = StoryTitleSpec.Anniversary(2020)
        assertEquals("On this day in 2020", StoryTitles.formatTitle(annivDefault))

        val tripDay = StoryTitleSpec.Trip("Rome", isSingleDay = true)
        assertEquals("Day in Rome", StoryTitles.formatTitle(tripDay))

        val tripMulti = StoryTitleSpec.Trip("Rome", isSingleDay = false)
        assertEquals("Trip to Rome", StoryTitles.formatTitle(tripMulti))

        val withOne = StoryTitleSpec.WithPerson(listOf("David"))
        assertEquals("With David", StoryTitles.formatTitle(withOne))

        val withTwo = StoryTitleSpec.WithPerson(listOf("David", "Anna"))
        assertEquals("With David & Anna", StoryTitles.formatTitle(withTwo))

        val sceneFood = StoryTitleSpec.Scene(StoryScene.Food)
        assertEquals("Food & Dining", StoryTitles.formatTitle(sceneFood))

        val sceneOther = StoryTitleSpec.Scene(StoryScene.Other("Museum"))
        assertEquals("Museum Highlights", StoryTitles.formatTitle(sceneOther))

        val outing = StoryTitleSpec.WeekdayOuting(5) // Friday
        assertEquals("Friday Outing", StoryTitles.formatTitle(outing, locale = Locale.US))

        val memories = StoryTitleSpec.MonthMemories(7, 2026) // July
        assertEquals("July Memories", StoryTitles.formatTitle(memories, locale = Locale.US))
    }

    @Test
    fun formatsStructuredStorySubtitleSpecs() {
        val subtitleWithPerson = StorySubtitleSpec(
            fromDay = day(2026, 5, 1),
            toDay = day(2026, 5, 3),
            withPerson = "Sarah",
        )
        val formattedPerson = StoryTitles.formatSubtitle(subtitleWithPerson, locale = Locale.US)
        assertTrue(formattedPerson.contains("With Sarah"))

        val subtitleWithPlace = StorySubtitleSpec(
            fromDay = day(2026, 5, 1),
            toDay = day(2026, 5, 1),
            placeName = "Abuja",
        )
        val formattedPlace = StoryTitles.formatSubtitle(subtitleWithPlace, locale = Locale.US)
        assertTrue(formattedPlace.contains("Abuja"))
    }
}
