package com.femi.gallery.domain.text

enum class TextScanStatus {
    /** Nothing is being read (it is finished, or has not started). */
    Idle,

    Scanning,

    /** Reading text did not work on this phone, so the text of photos cannot be searched. */
    Unavailable,
}

/** Progress of the background scan that reads the text in photos. */
data class TextScanState(
    val status: TextScanStatus = TextScanStatus.Idle,
    /** Photos read so far in this scan. */
    val scanned: Int = 0,
    /** Photos this scan has to read. */
    val total: Int = 0,
    /** Photos known to contain text (from this and earlier scans). */
    val withText: Int = 0,
)
