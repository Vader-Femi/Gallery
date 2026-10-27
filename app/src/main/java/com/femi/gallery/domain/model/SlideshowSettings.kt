package com.femi.gallery.domain.model

import java.util.Random

/** How long each picture of a slideshow stays on screen. */
enum class SlideshowSpeed(val millis: Long) {
    Slow(6_000L),
    Normal(3_500L),
    Fast(2_000L),
    ;

    /** The next one when the speed button is tapped: slow, normal, fast, and round again. */
    fun next(): SlideshowSpeed = entries[(ordinal + 1) % entries.size]

    companion object {
        val Default = Normal
    }
}

/** The order a shuffled slideshow plays in. */
object SlideshowOrder {

    /**
     * A random order of the positions 0 until [count] (each exactly once). The same [seed] gives the
     * same order, so a rotation of the phone does not reshuffle the show.
     */
    fun shuffled(count: Int, seed: Long): IntArray {
        val order = IntArray(count) { it }
        val random = Random(seed)
        for (i in count - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val kept = order[i]
            order[i] = order[j]
            order[j] = kept
        }
        return order
    }

    /** The position after [page] in a show of [count] pictures; the show starts over after the last. */
    fun pageAfter(page: Int, count: Int): Int = if (count <= 0) 0 else (page + 1) % count
}
