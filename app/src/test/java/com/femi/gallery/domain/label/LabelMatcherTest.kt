package com.femi.gallery.domain.label

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelMatcherTest {

    private fun matches(stored: String, term: String) = LabelMatcher.matches(stored, LabelMatcher.expand(term))

    @Test
    fun normalizeJoinsWordsWithUnderscores() {
        assertEquals("musical_instrument", LabelMatcher.normalize("Musical instrument"))
        assertEquals("dog", LabelMatcher.normalize("  Dog "))
        assertNull(LabelMatcher.normalize("  "))
    }

    @Test
    fun wholeWordsMatchButSubstringsInsideWordsDoNot() {
        assertTrue(matches("car", "car"))
        assertFalse(matches("card", "car"))
        assertFalse(matches("carnivore", "car")) // a dog photo is not a car photo
        assertFalse(matches("cartoon", "car"))
    }

    @Test
    fun longerPrefixesMatch() {
        assertTrue(matches("musical_instrument", "music"))
        assertTrue(matches("musical_instrument", "instrument"))
        assertFalse(matches("musical_instrument", "ument"))
    }

    @Test
    fun pluralsAreUnderstood() {
        assertTrue(matches("dog", "dogs"))
        assertTrue(matches("bus", "buses"))
        assertTrue(matches("city", "cities"))
    }

    @Test
    fun everydayWordsFindTheLabelsMlKitUses() {
        assertTrue(matches("dog", "puppy"))
        assertTrue(matches("fast_food", "snack"))
        assertTrue(matches("dish", "dinner"))
        assertTrue(matches("ocean", "sea"))
        assertTrue(matches("skyscraper", "city"))
    }

    @Test
    fun synonymsWithSeveralWordsMatchTheWholeLabel() {
        assertTrue(matches("mountainous_landforms", "mountain"))
        assertTrue(matches("mobile_phone", "phone"))
    }

    @Test
    fun unrelatedWordsDoNotMatch() {
        assertFalse(matches("dog", "cat"))
        assertFalse(matches("beach", "food"))
    }

    @Test
    fun blankSearchExpandsToNothing() {
        assertTrue(LabelMatcher.expand("  ").isEmpty())
    }
}
