package com.femi.gallery.domain.model

import kotlinx.serialization.Serializable

/**
 * An automatically found event: a day, or a few days in a row, with lots of photos.
 * [fromDay] and [toDay] are local calendar days (`LocalDate.toEpochDay()`), both included.
 */
data class Story(
    val fromDay: Long,
    val toDay: Long,
    val count: Int,
    /** content:// URI of a photo from the middle of the story. */
    val coverUri: String,
    /** The title the person chose, or null to show smart title / dates. */
    val title: String? = null,
    /** How many of the [count] items are videos. */
    val videoCount: Int = 0,
    /** Structured auto-generated smart title. */
    val smartTitle: StoryTitleSpec? = null,
    /** Structured contextual subtitle. */
    val subtitle: StorySubtitleSpec? = null,
    /** The story category: Trip, People, Scene, Anniversary, or Outing. */
    val theme: StoryTheme = StoryTheme.Outing,
) {
    /** The structured headline spec: custom title if set, else smart title, else date range. */
    val displayTitleSpec: StoryTitleSpec
        get() = title?.takeIf { it.isNotBlank() }?.let { StoryTitleSpec.Custom(it) }
            ?: smartTitle
            ?: StoryTitleSpec.DateRange(fromDay, toDay)
}

enum class StoryTheme {
    Trip,
    People,
    Scene,
    Anniversary,
    Outing,
}

@Serializable
sealed interface StoryTitleSpec {
    @Serializable
    data class Custom(val text: String) : StoryTitleSpec

    @Serializable
    data class Anniversary(
        val year: Int,
        val place: String? = null,
        val person: String? = null,
        val scene: StoryScene? = null,
    ) : StoryTitleSpec

    @Serializable
    data class Trip(
        val place: String,
        val isSingleDay: Boolean,
    ) : StoryTitleSpec

    @Serializable
    data class WithPerson(
        val names: List<String>,
    ) : StoryTitleSpec

    @Serializable
    data class Scene(
        val scene: StoryScene,
    ) : StoryTitleSpec

    @Serializable
    data class WeekdayOuting(
        val dayOfWeek: Int, // 1 (Monday) to 7 (Sunday)
    ) : StoryTitleSpec

    @Serializable
    data class MonthMemories(
        val month: Int, // 1 to 12
        val year: Int,
    ) : StoryTitleSpec

    @Serializable
    data class DateRange(
        val fromDay: Long,
        val toDay: Long,
    ) : StoryTitleSpec
}

@Serializable
sealed interface StoryScene {
    @Serializable data object Beach : StoryScene
    @Serializable data object Nature : StoryScene
    @Serializable data object Food : StoryScene
    @Serializable data object Pet : StoryScene
    @Serializable data object Celebration : StoryScene
    @Serializable data object Water : StoryScene
    @Serializable data object GoldenHour : StoryScene
    @Serializable data class Other(val label: String) : StoryScene

    companion object {
        fun fromLabel(scene: String): StoryScene = when (scene.lowercase()) {
            "beach", "ocean", "sea", "sand" -> Beach
            "nature", "mountain", "forest", "tree", "hiking" -> Nature
            "food", "dish", "meal", "restaurant", "dining" -> Food
            "pet", "dog", "cat", "animal", "puppy", "kitten" -> Pet
            "party", "birthday", "celebration" -> Celebration
            "lake", "water", "river" -> Water
            "sunset", "sunrise", "sky" -> GoldenHour
            else -> Other(scene.replaceFirstChar { it.uppercase() })
        }
    }
}

@Serializable
data class StorySubtitleSpec(
    val fromDay: Long,
    val toDay: Long,
    val withPerson: String? = null,
    val placeName: String? = null,
)
