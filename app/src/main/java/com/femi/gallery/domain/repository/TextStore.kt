package com.femi.gallery.domain.repository

import com.femi.gallery.domain.text.DetectedText

/** Where the text read from photos is kept between runs. */
interface TextStore {
    /** Everything saved, or an empty list when there is nothing (or it is damaged). */
    suspend fun load(): List<DetectedText>

    suspend fun save(entries: List<DetectedText>)
}
