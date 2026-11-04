package com.femi.gallery.domain.video

/** The part of a video that is kept, in milliseconds from its start. */
data class TrimRange(val startMs: Long, val endMs: Long) {
    val lengthMs: Long get() = endMs - startMs

    /** True when nothing is cut off, so there is nothing to export. */
    fun isWholeVideo(durationMs: Long): Boolean = startMs <= 0L && endMs >= durationMs
}

/** The rules of the trim screen, kept free of Android so that they can be tested. */
object TrimMath {
    /** No clip shorter than this can be made (a shorter video is trimmed to its own length at most). */
    const val MIN_LENGTH_MS = 500L

    fun whole(durationMs: Long): TrimRange = TrimRange(0L, durationMs.coerceAtLeast(0L))

    private fun minLength(durationMs: Long): Long = minOf(MIN_LENGTH_MS, durationMs.coerceAtLeast(0L))

    /** Moves the start handle; it cannot pass the end handle (less the minimum length) or go before 0. */
    fun withStart(range: TrimRange, startMs: Long, durationMs: Long): TrimRange {
        val latest = (range.endMs - minLength(durationMs)).coerceAtLeast(0L)
        return range.copy(startMs = startMs.coerceIn(0L, latest))
    }

    /** Moves the end handle; it cannot go before the start handle (plus the minimum length) or past the end. */
    fun withEnd(range: TrimRange, endMs: Long, durationMs: Long): TrimRange {
        val earliest = (range.startMs + minLength(durationMs)).coerceAtMost(durationMs)
        return range.copy(endMs = endMs.coerceIn(earliest, durationMs.coerceAtLeast(0L)))
    }

    /** A position on the bar (0 to 1) as a time in the video. */
    fun fractionToMs(fraction: Float, durationMs: Long): Long =
        (fraction.coerceIn(0f, 1f).toDouble() * durationMs).toLong()

    fun msToFraction(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0L) 0f else (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)

    /**
     * The last key frame at or before [positionMs], from [keyFramesMs] (sorted, in ms). A trimmed video can be
     * copied without re-encoding only from a key frame, so the start of the clip is moved back to one. With no key
     * frames known, the position is returned as it is.
     */
    fun keyFrameAtOrBefore(positionMs: Long, keyFramesMs: LongArray): Long {
        if (keyFramesMs.isEmpty()) return positionMs
        var low = 0
        var high = keyFramesMs.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (keyFramesMs[mid] <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return if (found >= 0) keyFramesMs[found] else keyFramesMs[0].coerceAtMost(positionMs)
    }

    /** The speeds supported by the video editor. */
    val SupportedSpeeds = listOf(0.5f, 1f, 2f)

    /** How much of the start of the clip is added by moving it back to a key frame. */
    fun extraAtStart(range: TrimRange, keyFramesMs: LongArray): Long =
        range.startMs - keyFrameAtOrBefore(range.startMs, keyFramesMs).coerceAtMost(range.startMs)

    /**
     * The name of the edited copy: the original's name without its extension, suffix and ".mp4" (the exporter
     * always writes MP4), and with counter "_2", "_3" and so on when that name is already [taken] (any letter case).
     */
    fun outputName(
        originalName: String,
        taken: Set<String>,
        speed: Float = 1f,
        isTrimmed: Boolean = true,
    ): String {
        val base = originalName.substringBeforeLast('.', originalName).ifBlank { "video" }
        val used = taken.mapTo(HashSet()) { it.lowercase() }
        val suffix = when {
            isTrimmed && speed != 1f -> {
                val speedStr = if (speed == speed.toLong().toFloat()) "${speed.toInt()}x" else "${speed}x"
                "_trim_$speedStr"
            }
            speed != 1f -> {
                val speedStr = if (speed == speed.toLong().toFloat()) "${speed.toInt()}x" else "${speed}x"
                "_$speedStr"
            }
            else -> "_trim"
        }
        var candidate = "${base}${suffix}.mp4"
        var n = 2
        while (candidate.lowercase() in used) {
            candidate = "${base}${suffix}_$n.mp4"
            n++
        }
        return candidate
    }

    /** "0:07", "1:05" or "1:02:03"; with [tenths], "0:07.4". */
    fun format(positionMs: Long, tenths: Boolean = false): String {
        val total = positionMs.coerceAtLeast(0L)
        val seconds = total / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val base = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        return if (tenths) base + "." + (total % 1000) / 100 else base
    }
}
