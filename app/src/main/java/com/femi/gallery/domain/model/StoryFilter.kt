package com.femi.gallery.domain.model

import com.femi.gallery.domain.search.SearchCriteria
import com.femi.gallery.domain.search.SearchQueryParser
import java.time.LocalDate

/** Which of a story's items to show. */
enum class StoryFilter(val mediaType: MediaType?, private val queryWord: String?) {
    All(null, null),
    Photos(MediaType.Image, "photos"),
    Videos(MediaType.Video, "videos"),
    ;

    /** The search for the story [from]..[to] limited to this kind of item. */
    fun criteria(from: LocalDate, to: LocalDate): SearchCriteria =
        SearchQueryParser.betweenDays(from, to).copy(mediaType = mediaType)

    /** The same search as text, so the viewer can reopen exactly what the grid shows. */
    fun query(from: LocalDate, to: LocalDate): String {
        val days = SearchQueryParser.betweenDaysQuery(from, to)
        return if (queryWord == null) days else "$days $queryWord"
    }
}
