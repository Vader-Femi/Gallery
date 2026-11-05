package com.femi.gallery.domain.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        locale: Locale = Locale.getDefault(),
    ): Pair<String, StoryTheme> {
        val from = LocalDate.ofEpochDay(minOf(fromDay, toDay))
        val isPastAnniversary = fromDay == toDay && StoryDetector.isAnniversary(from, today)

        if (isPastAnniversary) {
            val title = when {
                signals.placeName != null -> "${signals.placeName}, ${from.year}"
                signals.prominentPeople.isNotEmpty() -> "With ${signals.prominentPeople.first()}, ${from.year}"
                signals.topScene != null -> "${sceneDisplayName(signals.topScene)}, ${from.year}"
                else -> "On this day in ${from.year}"
            }
            return title to StoryTheme.Anniversary
        }

        if (signals.placeName != null) {
            val place = signals.placeName
            val title = if (fromDay == toDay) "Day in $place" else "Trip to $place"
            return title to StoryTheme.Trip
        }

        if (signals.prominentPeople.isNotEmpty()) {
            val people = signals.prominentPeople
            val title = if (people.size == 1) {
                "With ${people[0]}"
            } else {
                "With ${people[0]} & ${people[1]}"
            }
            return title to StoryTheme.People
        }

        if (signals.topScene != null) {
            val title = sceneDisplayName(signals.topScene)
            return title to StoryTheme.Scene
        }

        // Fallback Outing
        val title = if (fromDay == toDay) {
            val weekday = from.format(DateTimeFormatter.ofPattern("EEEE", locale))
            "$weekday Outing"
        } else {
            val month = from.format(DateTimeFormatter.ofPattern("MMMM", locale))
            "$month Memories"
        }
        return title to StoryTheme.Outing
    }

    fun buildSubtitle(
        fromDay: Long,
        toDay: Long,
        signals: ContextSignals,
        theme: StoryTheme,
        itemCount: Int,
        locale: Locale = Locale.getDefault(),
    ): String {
        val from = LocalDate.ofEpochDay(minOf(fromDay, toDay))
        val to = LocalDate.ofEpochDay(maxOf(fromDay, toDay))
        val monthAndDay = DateTimeFormatter.ofPattern("MMM d", locale)
        val dayOnly = DateTimeFormatter.ofPattern("d", locale)
        val withYear = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)

        val dateStr = when {
            from == to -> from.format(withYear)
            from.year != to.year -> "${from.format(withYear)} – ${to.format(withYear)}"
            from.month == to.month -> "${from.format(monthAndDay)}–${to.format(dayOnly)}, ${from.year}"
            else -> "${from.format(monthAndDay)} – ${to.format(monthAndDay)}, ${from.year}"
        }

        return when (theme) {
            StoryTheme.Trip -> {
                if (signals.prominentPeople.isNotEmpty()) {
                    "$dateStr • With ${signals.prominentPeople.first()}"
                } else {
                    dateStr
                }
            }
            StoryTheme.People -> {
                if (signals.placeName != null) {
                    "$dateStr • ${signals.placeName}"
                } else {
                    dateStr
                }
            }
            else -> dateStr
        }
    }

    private fun sceneDisplayName(scene: String): String = when (scene.lowercase()) {
        "beach", "ocean", "sea", "sand" -> "A Day at the Beach"
        "nature", "mountain", "forest", "tree", "hiking" -> "Out in Nature"
        "food", "dish", "meal", "restaurant", "dining" -> "Food & Dining"
        "pet", "dog", "cat", "animal", "puppy", "kitten" -> "Pet Highlights"
        "party", "birthday", "celebration" -> "Celebration"
        "lake", "water", "river" -> "By the Water"
        "sunset", "sunrise", "sky" -> "Golden Hour"
        else -> "${scene.replaceFirstChar { it.uppercase() }} Highlights"
    }
}
