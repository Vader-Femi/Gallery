package com.femi.gallery.domain.repository

/**
 * Turns photos and search words into vectors that point the same way when they mean the same thing
 * (CLIP-style), so a search can find photos by describing them.
 */
interface ImageEmbedder {
    /** False while there is no model to do the work; then nothing is scanned and searches skip this step. */
    val isAvailable: Boolean

    /**
     * A number that changes whenever the model changes (0 while there is none). Embeddings made by one model
     * mean nothing to another, so the stored ones are discarded when this is not the number they were made under.
     */
    val signature: Long

    suspend fun embed(mediaId: Long): FloatArray?
    suspend fun embedText(text: String): FloatArray?
}
