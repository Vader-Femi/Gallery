package com.femi.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideshowSettingsTest {

    @Test
    fun speedCyclesThroughAllThree() {
        assertEquals(SlideshowSpeed.Normal, SlideshowSpeed.Slow.next())
        assertEquals(SlideshowSpeed.Fast, SlideshowSpeed.Normal.next())
        assertEquals(SlideshowSpeed.Slow, SlideshowSpeed.Fast.next())
    }

    @Test
    fun fasterMeansShorter() {
        assertTrue(SlideshowSpeed.Fast.millis < SlideshowSpeed.Normal.millis)
        assertTrue(SlideshowSpeed.Normal.millis < SlideshowSpeed.Slow.millis)
    }

    @Test
    fun shuffleUsesEveryPositionOnce() {
        val order = SlideshowOrder.shuffled(500, 42L)
        assertEquals((0 until 500).toList(), order.sorted())
    }

    @Test
    fun shuffleActuallyShuffles() {
        val order = SlideshowOrder.shuffled(50, 7L)
        assertFalse(order.toList() == (0 until 50).toList())
    }

    @Test
    fun sameSeedSameOrderDifferentSeedDifferentOrder() {
        assertEquals(SlideshowOrder.shuffled(100, 1L).toList(), SlideshowOrder.shuffled(100, 1L).toList())
        assertFalse(SlideshowOrder.shuffled(100, 1L).toList() == SlideshowOrder.shuffled(100, 2L).toList())
    }

    @Test
    fun smallShowsWork() {
        assertEquals(0, SlideshowOrder.shuffled(0, 1L).size)
        assertEquals(listOf(0), SlideshowOrder.shuffled(1, 1L).toList())
    }

    @Test
    fun theShowStartsOverAfterTheLastPicture() {
        assertEquals(1, SlideshowOrder.pageAfter(0, 3))
        assertEquals(0, SlideshowOrder.pageAfter(2, 3))
        assertEquals(0, SlideshowOrder.pageAfter(0, 0))
    }
}
