package com.femi.gallery.domain.clip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptionQueryTest {

    @Test
    fun wordsDescribeAPhoto() {
        assertTrue(DescriptionQuery.isWorthDescribing("dog"))
        assertTrue(DescriptionQuery.isWorthDescribing("sunset over the sea"))
        assertTrue(DescriptionQuery.isWorthDescribing("a red car"))
    }

    @Test
    fun numbersAndDatesDoNot() {
        assertFalse(DescriptionQuery.isWorthDescribing("2019"))
        assertFalse(DescriptionQuery.isWorthDescribing("15"))
    }

    @Test
    fun fileNamesDoNot() {
        assertFalse(DescriptionQuery.isWorthDescribing("img_20260101_0042"))
        assertFalse(DescriptionQuery.isWorthDescribing("pxl 20260101 123456"))
    }

    @Test
    fun veryShortWordsAndNothingDoNot() {
        assertFalse(DescriptionQuery.isWorthDescribing(""))
        assertFalse(DescriptionQuery.isWorthDescribing("a"))
        assertFalse(DescriptionQuery.isWorthDescribing("of it"))
    }

    @Test
    fun otherAlphabetsCount() {
        assertTrue(DescriptionQuery.isWorthDescribing("zachód słońca"))
    }
}
