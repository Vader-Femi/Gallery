package com.femi.gallery.domain.search

import java.time.LocalDate

/**
 * What the filter chips under the search box do to the text of the query: add a word the search understands, take it
 * out again, or set a date range. Kept apart from the screen so that it can be tested.
 */
object SearchQueryEditor {
    enum class Chip(val word: String, internal val own: Regex, internal val opposite: Regex?) {
        Videos("videos", Regex("\\bvideos?\\b", RegexOption.IGNORE_CASE), PHOTO_WORDS),
        Photos("photos", PHOTO_WORDS, Regex("\\bvideos?\\b", RegexOption.IGNORE_CASE)),
        Favorites("favorites", Regex("\\b(favorites?|favourites?)\\b", RegexOption.IGNORE_CASE), null),
        Screenshots("screenshots", Regex("\\bscreenshots?\\b", RegexOption.IGNORE_CASE), null),
    }

    private val FROM_TOKEN = Regex("\\bfrom:\\S+", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("\\s+")

    /** True when the query already has the chip's word, in any of its spellings ("video", "Favourite", ...). */
    fun isSelected(query: String, chip: Chip): Boolean = chip.own.containsMatchIn(query)

    /**
     * Takes the chip's word out when it is there (whatever the spelling), otherwise puts it at the end; the word of
     * the opposite chip (videos against photos) is taken out when the chip is turned on.
     */
    fun toggle(query: String, chip: Chip): String =
        if (isSelected(query, chip)) {
            remove(query, chip.own)
        } else {
            val cleaned = chip.opposite?.let { remove(query, it) } ?: query.trim()
            if (cleaned.isEmpty()) chip.word else "$cleaned ${chip.word}"
        }

    /** The start of "the last 30 days" as it is written in a query. */
    fun last30Token(today: LocalDate): String = "from:${today.minusDays(30)}"

    /** True when the query has exactly the "last 30 days" date (a date of the person's own is not this chip). */
    fun isLast30(query: String, today: LocalDate): Boolean {
        val token = last30Token(today)
        return FROM_TOKEN.findAll(query).any { it.value.equals(token, ignoreCase = true) }
    }

    /** Takes the "last 30 days" date out, or sets it, replacing any other start date so that there is only one. */
    fun toggleLast30(query: String, today: LocalDate): String {
        val token = last30Token(today)
        if (isLast30(query, today)) return remove(query, FROM_TOKEN)
        val cleaned = remove(query, FROM_TOKEN)
        return if (cleaned.isEmpty()) token else "$cleaned $token"
    }

    private fun remove(query: String, pattern: Regex): String = query.replace(pattern, " ").replace(SPACES, " ").trim()

    private val PHOTO_WORDS = Regex("\\b(photos?|pictures?|images?)\\b", RegexOption.IGNORE_CASE)
}
