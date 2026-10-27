package com.femi.gallery.domain.model

import com.femi.gallery.domain.search.SearchQueryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StoryFilterTest {
    private val from = LocalDate.of(2026, 10, 1)
    private val to = LocalDate.of(2026, 10, 3)

    @Test
    fun theQueryOpensExactlyWhatTheGridShows() {
        for (filter in StoryFilter.entries) {
            assertEquals(filter.name, filter.criteria(from, to), SearchQueryParser.parse(filter.query(from, to)))
        }
    }

    @Test
    fun filtersSetTheMediaType() {
        assertNull(StoryFilter.All.criteria(from, to).mediaType)
        assertEquals(MediaType.Image, StoryFilter.Photos.criteria(from, to).mediaType)
        assertEquals(MediaType.Video, StoryFilter.Videos.criteria(from, to).mediaType)
    }
}
