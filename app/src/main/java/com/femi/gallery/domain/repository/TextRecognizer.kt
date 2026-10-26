package com.femi.gallery.domain.repository

/** Reads the text in a photo. */
interface TextRecognizer {
    /**
     * The text in the photo with [mediaId]: an empty string when there is none (or the file cannot be
     * read as an image), null when the photo could not be found, so it is tried again later.
     *
     * @throws Exception when reading text does not work at all on this phone
     */
    suspend fun recognize(mediaId: Long): String?
}
