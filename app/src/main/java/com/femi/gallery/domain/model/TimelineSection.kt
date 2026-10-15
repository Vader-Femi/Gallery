package com.femi.gallery.domain.model

/** A run of consecutive media items captured on the same local calendar day. */
data class TimelineSection(
    val epochDay: Long,
    /** Index of the section's first item in the full, newest-first media list. */
    val startIndex: Int,
    val count: Int,
)
