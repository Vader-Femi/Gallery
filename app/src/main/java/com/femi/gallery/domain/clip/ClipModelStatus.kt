package com.femi.gallery.domain.clip

/** Which of the two model files (one for pictures, one for text) are installed. */
enum class ClipModelStatus {
    NotInstalled,
    ImageOnly,
    TextOnly,
    Installed,
    ;

    val isComplete: Boolean get() = this == Installed

    companion object {
        fun of(hasImageModel: Boolean, hasTextModel: Boolean): ClipModelStatus = when {
            hasImageModel && hasTextModel -> Installed
            hasImageModel -> ImageOnly
            hasTextModel -> TextOnly
            else -> NotInstalled
        }
    }
}

/**
 * A number that changes when either model file changes. Embeddings made by one model mean nothing to another,
 * so when this number is not the one the stored embeddings were made under, they are discarded and the photos
 * are read again.
 */
object ClipModelSignature {
    fun of(imageBytes: Long, imageModified: Long, textBytes: Long, textModified: Long): Long {
        var h = 1125899906842597L
        for (v in longArrayOf(imageBytes, imageModified, textBytes, textModified)) h = 31 * h + v
        return if (h == 0L) 1L else h // 0 is kept for "no model"
    }
}
