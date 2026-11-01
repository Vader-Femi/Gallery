package com.femi.gallery.domain.repository

import com.femi.gallery.domain.clip.DetectedEmbedding

interface ClipStore {
    suspend fun load(): List<DetectedEmbedding>
    suspend fun save(entries: List<DetectedEmbedding>)

    /** The [ImageEmbedder.signature] the stored embeddings were made under, or null if none was recorded. */
    suspend fun modelSignature(): Long?

    suspend fun setModelSignature(value: Long)
}
