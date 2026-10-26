package com.femi.gallery.domain.text

/**
 * The text read from one photo, or an empty [text] when it has none (so the photo is not read
 * again). [modified] is the file's DATE_MODIFIED when it was read; a photo that has changed since is
 * read again.
 */
data class DetectedText(
    val id: Long,
    val modified: Long,
    val text: String,
)
