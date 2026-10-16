package com.femi.gallery.domain.search

import com.femi.gallery.domain.model.MediaType
import java.time.LocalDate
import java.time.ZoneId

/**
 * Turns what the user typed into [SearchCriteria].
 *
 * Understands:
 * - types: `videos`, `photos` / `pictures` / `images`
 * - `favorites`
 * - dates: `today`, `yesterday`, a year (`2025`), a month (`october`, `oct`), a month and year
 *   (`october 2025`) or a day (`oct 15`, `15 oct`, `15 oct 2025`)
 * - anything else is matched against file and folder names (`screenshot`, `whatsapp`, `img_2031`)
 *
 * A month without a year matches that month in every year.
 */
object SearchQueryParser {

    private const val FIRST_YEAR = 2000

    private val months: Map<String, Int> = buildMap {
        val names = listOf(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
        )
        names.forEachIndexed { index, name ->
            put(name, index + 1)
            put(name.take(3), index + 1)
        }
        put("sept", 9)
    }

    private val videoWords = setOf("video", "videos")
    private val photoWords = setOf("photo", "photos", "picture", "pictures", "image", "images")
    private val favoriteWords = setOf("favorite", "favorites", "favourite", "favourites")

    fun parse(
        query: String,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): SearchCriteria {
        val tokens = query.lowercase()
            .replace('%', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }

        var mediaType: MediaType? = null
        var favoritesOnly = false
        var relativeDay: LocalDate? = null
        var year: Int? = null
        var month: Int? = null
        var day: Int? = null
        val consumed = BooleanArray(tokens.size)

        // First pass: words with a fixed meaning.
        tokens.forEachIndexed { i, token ->
            when {
                token in videoWords -> { mediaType = MediaType.Video; consumed[i] = true }
                token in photoWords -> { mediaType = MediaType.Image; consumed[i] = true }
                token in favoriteWords -> { favoritesOnly = true; consumed[i] = true }
                token == "today" -> { relativeDay = today; consumed[i] = true }
                token == "yesterday" -> { relativeDay = today.minusDays(1); consumed[i] = true }
                token in months -> { month = months.getValue(token); consumed[i] = true }
                isYear(token) -> { year = token.toInt(); consumed[i] = true }
            }
        }

        // Second pass: a day number right next to a month ("oct 15" / "15 oct").
        if (month != null && relativeDay == null) {
            val monthIndex = tokens.indexOfFirst { it in months }
            val neighbours = listOf(monthIndex + 1, monthIndex - 1)
            for (i in neighbours) {
                val candidate = tokens.getOrNull(i)?.toIntOrNull()
                if (i in tokens.indices && !consumed[i] && candidate != null && candidate in 1..31) {
                    day = candidate
                    consumed[i] = true
                    break
                }
            }
        }

        val dateRanges = when {
            relativeDay != null -> listOf(dayRange(relativeDay!!, zone))
            month != null && year != null && day != null ->
                listOfNotNull(runCatching { dayRange(LocalDate.of(year!!, month!!, day!!), zone) }.getOrNull())
            month != null && year != null -> listOf(monthRange(year!!, month!!, zone))
            month != null && day != null ->
                (FIRST_YEAR..today.year).mapNotNull { y ->
                    runCatching { dayRange(LocalDate.of(y, month!!, day!!), zone) }.getOrNull()
                }
            month != null -> (FIRST_YEAR..today.year).map { y -> monthRange(y, month!!, zone) }
            year != null -> listOf(yearRange(year!!, zone))
            else -> emptyList()
        }

        val terms = tokens.filterIndexed { i, _ -> !consumed[i] }

        return SearchCriteria(
            terms = terms,
            dateRanges = dateRanges,
            mediaType = mediaType,
            favoritesOnly = favoritesOnly,
        )
    }

    private fun isYear(token: String): Boolean =
        token.length == 4 && token.all { it.isDigit() } && token.toInt() in 1990..2100

    private fun dayRange(date: LocalDate, zone: ZoneId): LongRange =
        rangeOf(date, date.plusDays(1), zone)

    private fun monthRange(year: Int, month: Int, zone: ZoneId): LongRange {
        val start = LocalDate.of(year, month, 1)
        return rangeOf(start, start.plusMonths(1), zone)
    }

    private fun yearRange(year: Int, zone: ZoneId): LongRange =
        rangeOf(LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1), zone)

    /** [start, end) as an inclusive epoch-second range. */
    private fun rangeOf(start: LocalDate, end: LocalDate, zone: ZoneId): LongRange =
        start.atStartOfDay(zone).toEpochSecond()..(end.atStartOfDay(zone).toEpochSecond() - 1)
}
