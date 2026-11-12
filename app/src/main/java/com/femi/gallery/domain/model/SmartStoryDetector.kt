package com.femi.gallery.domain.model

import java.time.LocalDate

object SmartStoryDetector {

    data class ContextSignals(
        val placeName: String? = null,
        val prominentPeople: List<String> = emptyList(),
        val topScene: String? = null,
    )

    fun determineTitleAndTheme(
        fromDay: Long,
        toDay: Long,
        signals: ContextSignals,
        today: LocalDate = LocalDate.now(),
    ): Pair<StoryTitleSpec, StoryTheme> {
        val from = LocalDate.ofEpochDay(minOf(fromDay, toDay))
        val isPastAnniversary = fromDay == toDay && StoryDetector.isAnniversary(from, today)

        if (isPastAnniversary) {
            val titleSpec = when {
                signals.placeName != null -> StoryTitleSpec.Anniversary(year = from.year, place = signals.placeName)
                signals.prominentPeople.isNotEmpty() -> StoryTitleSpec.Anniversary(year = from.year, person = signals.prominentPeople.first())
                signals.topScene != null -> StoryTitleSpec.Anniversary(year = from.year, scene = StoryScene.fromLabel(signals.topScene))
                else -> StoryTitleSpec.Anniversary(year = from.year)
            }
            return titleSpec to StoryTheme.Anniversary
        }

        if (signals.placeName != null) {
            val titleSpec = StoryTitleSpec.Trip(place = signals.placeName, isSingleDay = fromDay == toDay)
            return titleSpec to StoryTheme.Trip
        }

        if (signals.prominentPeople.isNotEmpty()) {
            val titleSpec = StoryTitleSpec.WithPerson(names = signals.prominentPeople.take(2))
            return titleSpec to StoryTheme.People
        }

        if (signals.topScene != null) {
            val titleSpec = StoryTitleSpec.Scene(scene = StoryScene.fromLabel(signals.topScene))
            return titleSpec to StoryTheme.Scene
        }

        // Fallback Outing
        val titleSpec = if (fromDay == toDay) {
            StoryTitleSpec.WeekdayOuting(dayOfWeek = from.dayOfWeek.value)
        } else {
            StoryTitleSpec.MonthMemories(month = from.monthValue, year = from.year)
        }
        return titleSpec to StoryTheme.Outing
    }

    fun buildSubtitle(
        fromDay: Long,
        toDay: Long,
        signals: ContextSignals,
        theme: StoryTheme,
        itemCount: Int,
    ): StorySubtitleSpec {
        val withPerson = if (theme == StoryTheme.Trip && signals.prominentPeople.isNotEmpty()) {
            signals.prominentPeople.first()
        } else null

        val placeName = if (theme == StoryTheme.People && signals.placeName != null) {
            signals.placeName
        } else null

        return StorySubtitleSpec(
            fromDay = fromDay,
            toDay = toDay,
            withPerson = withPerson,
            placeName = placeName,
        )
    }
}
