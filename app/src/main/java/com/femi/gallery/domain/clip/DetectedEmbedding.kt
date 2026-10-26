package com.femi.gallery.domain.clip

data class DetectedEmbedding(
    val id: Long,
    val modified: Long,
    val embedding: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DetectedEmbedding
        return id == other.id && modified == other.modified && embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + modified.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
