package com.femi.gallery.domain.search

import com.femi.gallery.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SearchQueryParserTest {

    private val today = LocalDate.of(2026, 10, 16)

    private fun parse(query: String) = SearchQueryParser.parse(query, today, ZoneOffset.UTC)

    private fun epoch(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toEpochSecond()

    @Test
    fun blankQueryIsEmpty() {
        assertTrue(parse("").isEmpty)
        assertTrue(parse("   ").isEmpty)
    }

    @Test
    fun plainWordsBecomeNameTerms() {
        assertEquals(listOf("whatsapp", "img_20"), parse("WhatsApp img_20").terms)
    }

    @Test
    fun typeAndFavoriteKeywords() {
        val criteria = parse("favorite videos")
        assertEquals(MediaType.Video, criteria.mediaType)
        assertTrue(criteria.favoritesOnly)
        assertTrue(criteria.terms.isEmpty())
        assertEquals(MediaType.Image, parse("photos").mediaType)
    }

    @Test
    fun todayAndYesterday() {
        assertEquals(listOf(epoch(2026, 10, 16)..(epoch(2026, 10, 17) - 1)), parse("today").dateRanges)
        assertEquals(listOf(epoch(2026, 10, 15)..(epoch(2026, 10, 16) - 1)), parse("yesterday").dateRanges)
    }

    @Test
    fun monthAndYear() {
        val ranges = parse("october 2025").dateRanges
        assertEquals(listOf(epoch(2025, 10, 1)..(epoch(2025, 11, 1) - 1)), ranges)
    }

    @Test
    fun yearOnly() {
        assertEquals(listOf(epoch(2024, 1, 1)..(epoch(2025, 1, 1) - 1)), parse("2024").dateRanges)
    }

    @Test
    fun monthWithoutYearMatchesEveryYear() {
        val ranges = parse("oct").dateRanges
        assertEquals(2026 - 2000 + 1, ranges.size)
        assertEquals(epoch(2000, 10, 1), ranges.first().first)
    }

    @Test
    fun dayNextToMonthInEitherOrder() {
        val expected = listOf(epoch(2025, 10, 15)..(epoch(2025, 10, 16) - 1))
        assertEquals(expected, parse("oct 15 2025").dateRanges)
        assertEquals(expected, parse("15 oct 2025").dateRanges)
        assertTrue(parse("oct 15").dateRanges.isNotEmpty())
    }

    @Test
    fun numbersThatAreNotDatesStayAsTerms() {
        val criteria = parse("img 1234")
        assertEquals(listOf("img", "1234"), criteria.terms)
        assertTrue(criteria.dateRanges.isEmpty())
    }

    @Test
    fun combinesDatesWithNames() {
        val criteria = parse("screenshot october")
        assertEquals(listOf("screenshot"), criteria.terms)
        assertTrue(criteria.dateRanges.isNotEmpty())
        assertNull(criteria.mediaType)
    }
}
