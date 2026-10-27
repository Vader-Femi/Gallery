package com.femi.gallery.domain.label

import org.junit.Assert.assertEquals
import org.junit.Test

class LabelIndexTest {

    private val index = LabelIndex(
        listOf(
            DetectedLabel(1, 10, "dog pet mammal"),
            DetectedLabel(2, 10, "beach sky sea"),
            DetectedLabel(3, 10, ""),
            DetectedLabel(4, 10, "fast_food dish"),
        ),
    )

    @Test
    fun findsPhotosByLabel() {
        assertEquals(setOf(1L), index.idsMatching("dog"))
    }

    @Test
    fun findsPhotosBySynonym() {
        assertEquals(setOf(1L), index.idsMatching("puppy"))
        assertEquals(setOf(2L), index.idsMatching("ocean"))
        assertEquals(setOf(4L), index.idsMatching("food"))
    }

    @Test
    fun photosWithoutLabelsAreNeverReturned() {
        assertEquals(emptySet<Long>(), index.idsMatching("nothing"))
        assertEquals(4, index.readCount)
        assertEquals(3, index.withLabelCount)
    }
}
