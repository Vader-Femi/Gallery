package com.femi.gallery.domain.clip

/**
 * What the image model made of one photo: a vector of numbers that points the same way as the vector of a
 * description of the photo. It is kept as one byte per number (see [ClipQuantizer]), which is accurate enough
 * to compare and keeps 50,000 photos at about 25 MB instead of 100 MB.
 *
 * An empty [embedding] means the photo was looked at and could not be read, so it is not tried again until it changes.
 */
data class DetectedEmbedding(
    val id: Long,
    /** The photo's last-modified time when it was scanned; an edited photo is scanned again. */
    val modified: Long,
    val embedding: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DetectedEmbedding) return false
        return id == other.id && modified == other.modified && embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + modified.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
