package com.femi.gallery.presentation.stories

import android.content.Context
import android.content.res.Resources
import com.femi.gallery.R
import com.femi.gallery.domain.model.StoryDetector
import com.femi.gallery.domain.model.StoryScene
import com.femi.gallery.domain.model.StorySubtitleSpec
import com.femi.gallery.domain.model.StoryTitleSpec
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Titles and subtitles for stories, dynamically resolved using application resources and locale. */
object StoryTitles {

    /**
     * Formats a structured [StoryTitleSpec] into a localized display string.
     */
    fun formatTitle(
        context: Context,
        spec: StoryTitleSpec,
        today: LocalDate = LocalDate.now(),
    ): String = formatTitle(
        spec = spec,
        resources = context.resources,
        today = today,
        locale = context.resources.configuration.locales[0] ?: Locale.getDefault(),
    )

    /**
     * Formats a structured [StoryTitleSpec] with optional [Resources] (for unit tests without Context).
     */
    fun formatTitle(
        spec: StoryTitleSpec,
        resources: Resources? = null,
        today: LocalDate = LocalDate.now(),
        locale: Locale = Locale.getDefault(),
    ): String = when (spec) {
        is StoryTitleSpec.Custom -> spec.text
        is StoryTitleSpec.Anniversary -> {
            when {
                spec.place != null -> {
                    resources?.getString(R.string.story_title_anniversary_place, spec.place, spec.year)
                        ?: "${spec.place}, ${spec.year}"
                }
                spec.person != null -> {
                    resources?.getString(R.string.story_title_anniversary_person, spec.person, spec.year)
                        ?: "With ${spec.person}, ${spec.year}"
                }
                spec.scene != null -> {
                    val sceneName = formatScene(spec.scene, resources)
                    resources?.getString(R.string.story_title_anniversary_scene, sceneName, spec.year)
                        ?: "$sceneName, ${spec.year}"
                }
                else -> {
                    resources?.getString(R.string.story_title_anniversary, spec.year)
                        ?: "On this day in ${spec.year}"
                }
            }
        }
        is StoryTitleSpec.Trip -> {
            if (spec.isSingleDay) {
                resources?.getString(R.string.story_title_day_in, spec.place)
                    ?: "Day in ${spec.place}"
            } else {
                resources?.getString(R.string.story_title_trip_to, spec.place)
                    ?: "Trip to ${spec.place}"
            }
        }
        is StoryTitleSpec.WithPerson -> {
            when (spec.names.size) {
                0 -> ""
                1 -> resources?.getString(R.string.story_title_with_person, spec.names[0])
                    ?: "With ${spec.names[0]}"
                else -> resources?.getString(R.string.story_title_with_two_people, spec.names[0], spec.names[1])
                    ?: "With ${spec.names[0]} & ${spec.names[1]}"
            }
        }
        is StoryTitleSpec.Scene -> formatScene(spec.scene, resources)
        is StoryTitleSpec.WeekdayOuting -> {
            val dayOfWeek = java.time.DayOfWeek.of(spec.dayOfWeek)
            val weekdayName = dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            resources?.getString(R.string.story_title_weekday_outing, weekdayName)
                ?: "$weekdayName Outing"
        }
        is StoryTitleSpec.MonthMemories -> {
            val month = java.time.Month.of(spec.month)
            val monthName = month.getDisplayName(TextStyle.FULL, locale)
            resources?.getString(R.string.story_title_month_memories, monthName)
                ?: "$monthName Memories"
        }
        is StoryTitleSpec.DateRange -> range(spec.fromDay, spec.toDay, today, locale, resources)
    }

    /**
     * Formats a [StoryScene] into a localized name.
     */
    fun formatScene(scene: StoryScene, resources: Resources?): String = when (scene) {
        StoryScene.Beach -> resources?.getString(R.string.story_scene_beach) ?: "A Day at the Beach"
        StoryScene.Nature -> resources?.getString(R.string.story_scene_nature) ?: "Out in Nature"
        StoryScene.Food -> resources?.getString(R.string.story_scene_food) ?: "Food & Dining"
        StoryScene.Pet -> resources?.getString(R.string.story_scene_pet) ?: "Pet Highlights"
        StoryScene.Celebration -> resources?.getString(R.string.story_scene_celebration) ?: "Celebration"
        StoryScene.Water -> resources?.getString(R.string.story_scene_water) ?: "By the Water"
        StoryScene.GoldenHour -> resources?.getString(R.string.story_scene_golden_hour) ?: "Golden Hour"
        is StoryScene.Other -> resources?.getString(R.string.story_scene_other, scene.label) ?: "${scene.label} Highlights"
    }

    /**
     * Formats a structured [StorySubtitleSpec] into a localized display string.
     */
    fun formatSubtitle(
        context: Context,
        spec: StorySubtitleSpec,
    ): String = formatSubtitle(
        spec = spec,
        resources = context.resources,
        locale = context.resources.configuration.locales[0] ?: Locale.getDefault(),
    )

    /**
     * Formats a structured [StorySubtitleSpec] with optional [Resources] (for unit tests without Context).
     */
    fun formatSubtitle(
        spec: StorySubtitleSpec,
        resources: Resources? = null,
        locale: Locale = Locale.getDefault(),
    ): String {
        val from = LocalDate.ofEpochDay(minOf(spec.fromDay, spec.toDay))
        val to = LocalDate.ofEpochDay(maxOf(spec.fromDay, spec.toDay))
        val monthAndDay = DateTimeFormatter.ofPattern("MMM d", locale)
        val dayOnly = DateTimeFormatter.ofPattern("d", locale)
        val withYear = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)

        val dateStr = when {
            from == to -> from.format(withYear)
            from.year != to.year -> "${from.format(withYear)} – ${to.format(withYear)}"
            from.month == to.month -> "${from.format(monthAndDay)}–${to.format(dayOnly)}, ${from.year}"
            else -> "${from.format(monthAndDay)} – ${to.format(monthAndDay)}, ${from.year}"
        }

        return when {
            spec.withPerson != null -> {
                resources?.getString(R.string.story_subtitle_with_person, dateStr, spec.withPerson)
                    ?: "$dateStr • With ${spec.withPerson}"
            }
            spec.placeName != null -> {
                resources?.getString(R.string.story_subtitle_with_place, dateStr, spec.placeName)
                    ?: "$dateStr • ${spec.placeName}"
            }
            else -> dateStr
        }
    }

    /**
     * `Sat, Oct 3` for one day, `Oct 3 – 5` or `Oct 30 – Nov 2` for several; the year is added
     * when it isn't the current one. "On this day in YYYY" for past anniversaries.
     */
    fun range(
        fromDay: Long,
        toDay: Long,
        today: LocalDate = LocalDate.now(),
        locale: Locale = Locale.getDefault(),
        resources: Resources? = null,
    ): String {
        val from = LocalDate.ofEpochDay(minOf(fromDay, toDay))
        val to = LocalDate.ofEpochDay(maxOf(fromDay, toDay))
        val weekdayAndDay = DateTimeFormatter.ofPattern("EEE, MMM d", locale)
        val monthAndDay = DateTimeFormatter.ofPattern("MMM d", locale)
        val dayOnly = DateTimeFormatter.ofPattern("d", locale)
        val withYear = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)

        val isPastOnThisDay = from == to && StoryDetector.isAnniversary(from, today)
        if (isPastOnThisDay) {
            return resources?.getString(R.string.story_title_anniversary, from.year)
                ?: "On this day in ${from.year}"
        }

        return when {
            from == to && from.year == today.year -> from.format(weekdayAndDay)
            from == to -> from.format(withYear)
            from.year != to.year -> "${from.format(withYear)} – ${to.format(withYear)}"
            else -> {
                val end = if (from.month == to.month) to.format(dayOnly) else to.format(monthAndDay)
                val text = "${from.format(monthAndDay)} – $end"
                if (from.year == today.year) text else "$text, ${from.year}"
            }
        }
    }
}
