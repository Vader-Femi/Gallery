package com.femi.gallery.domain.scan

/**
 * Decides when the progress notification is worth updating. The scans report their progress every second,
 * but a notification that is redrawn every second wastes battery and the system may drop updates it
 * considers too frequent. It is updated when the numbers have changed and a few seconds have passed since
 * the last update, or at once when the total changed (a new scan began or an old one finished).
 */
class ProgressThrottle(private val minIntervalMs: Long = DEFAULT_INTERVAL_MS) {
    private var shownScanned = -1
    private var shownTotal = -1
    private var shownAtMs = 0L

    fun shouldUpdate(scanned: Int, total: Int, nowMs: Long): Boolean {
        val first = shownTotal < 0
        val changed = scanned != shownScanned || total != shownTotal
        val totalChanged = total != shownTotal
        val due = nowMs - shownAtMs >= minIntervalMs
        val update = first || totalChanged || (changed && due)
        if (update) {
            shownScanned = scanned
            shownTotal = total
            shownAtMs = nowMs
        }
        return update
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 3_000L
    }
}
