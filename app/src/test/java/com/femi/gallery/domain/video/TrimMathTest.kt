package com.femi.gallery.domain.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimMathTest {
    private val duration = 10_000L

    @Test
    fun wholeVideoIsRecognised() {
        assertTrue(TrimMath.whole(duration).isWholeVideo(duration))
        assertFalse(TrimRange(1, duration).isWholeVideo(duration))
        assertFalse(TrimRange(0, duration - 1).isWholeVideo(duration))
    }

    @Test
    fun startCannotPassTheEndLessTheMinimumLength() {
        val r = TrimRange(0, 4_000)
        assertEquals(3_500L, TrimMath.withStart(r, 9_000, duration).startMs)
        assertEquals(0L, TrimMath.withStart(r, -50, duration).startMs)
        assertEquals(1_000L, TrimMath.withStart(r, 1_000, duration).startMs)
    }

    @Test
    fun endCannotGoBeforeTheStartPlusTheMinimumLengthOrPastTheDuration() {
        val r = TrimRange(6_000, 10_000)
        assertEquals(6_500L, TrimMath.withEnd(r, 1_000, duration).endMs)
        assertEquals(duration, TrimMath.withEnd(r, 99_000, duration).endMs)
        assertEquals(8_000L, TrimMath.withEnd(r, 8_000, duration).endMs)
    }

    @Test
    fun aVideoShorterThanTheMinimumCanStillBeHandled() {
        val r = TrimMath.whole(300)
        assertEquals(0L, TrimMath.withStart(r, 250, 300).startMs)
        assertEquals(300L, TrimMath.withEnd(r, 10, 300).endMs)
    }

    @Test
    fun handlesNeverCrossWhatEverTheOrderOfMoves() {
        var r = TrimMath.whole(duration)
        r = TrimMath.withStart(r, 9_900, duration)
        r = TrimMath.withEnd(r, 0, duration)
        r = TrimMath.withStart(r, 8_000, duration)
        assertTrue(r.lengthMs >= TrimMath.MIN_LENGTH_MS)
        assertTrue(r.startMs >= 0 && r.endMs <= duration)
    }

    @Test
    fun fractionsAndTimesConvertBothWays() {
        assertEquals(2_500L, TrimMath.fractionToMs(0.25f, duration))
        assertEquals(0L, TrimMath.fractionToMs(-1f, duration))
        assertEquals(duration, TrimMath.fractionToMs(3f, duration))
        assertEquals(0.5f, TrimMath.msToFraction(5_000, duration), 0.0001f)
        assertEquals(0f, TrimMath.msToFraction(5_000, 0), 0f)
    }

    @Test
    fun keyFrameLookupFindsTheLastOneAtOrBefore() {
        val keys = longArrayOf(0, 2_000, 4_000, 6_000)
        assertEquals(2_000L, TrimMath.keyFrameAtOrBefore(3_999, keys))
        assertEquals(4_000L, TrimMath.keyFrameAtOrBefore(4_000, keys))
        assertEquals(6_000L, TrimMath.keyFrameAtOrBefore(9_999, keys))
        assertEquals(0L, TrimMath.keyFrameAtOrBefore(0, keys))
    }

    @Test
    fun keyFrameLookupWithoutKeyFramesOrBeforeTheFirstOne() {
        assertEquals(1_234L, TrimMath.keyFrameAtOrBefore(1_234, longArrayOf()))
        assertEquals(100L, TrimMath.keyFrameAtOrBefore(100, longArrayOf(500, 2_000)))
    }

    @Test
    fun extraAtStartIsTheDistanceBackToTheKeyFrame() {
        val keys = longArrayOf(0, 2_000, 4_000)
        assertEquals(1_500L, TrimMath.extraAtStart(TrimRange(3_500, 9_000), keys))
        assertEquals(0L, TrimMath.extraAtStart(TrimRange(4_000, 9_000), keys))
        assertEquals(0L, TrimMath.extraAtStart(TrimRange(3_500, 9_000), longArrayOf()))
    }

    @Test
    fun outputNameKeepsTheBaseAndAvoidsNamesInUse() {
        assertEquals("VID_1_trim.mp4", TrimMath.outputName("VID_1.mp4", emptySet()))
        assertEquals("VID_1_trim.mp4", TrimMath.outputName("VID_1.MOV", emptySet()))
        assertEquals("VID_1_trim_2.mp4", TrimMath.outputName("VID_1.mp4", setOf("vid_1_trim.MP4")))
        assertEquals("VID_1_trim_3.mp4", TrimMath.outputName("VID_1.mp4", setOf("VID_1_trim.mp4", "VID_1_trim_2.mp4")))
        assertEquals("a.b_trim.mp4", TrimMath.outputName("a.b.mp4", emptySet()))
        assertEquals("video_trim.mp4", TrimMath.outputName("", emptySet()))
        assertEquals("clip_trim.mp4", TrimMath.outputName("clip", emptySet()))
    }

    @Test
    fun timesAreFormatted() {
        assertEquals("0:07", TrimMath.format(7_400))
        assertEquals("1:05", TrimMath.format(65_000))
        assertEquals("1:02:03", TrimMath.format(3_723_000))
        assertEquals("0:07.4", TrimMath.format(7_450, tenths = true))
        assertEquals("0:00", TrimMath.format(-5))
    }
}
