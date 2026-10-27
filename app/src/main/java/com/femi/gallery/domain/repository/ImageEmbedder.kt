package com.femi.gallery.domain.repository

/**
 * Turns photos and search words into vectors that point the same way when they mean the same thing
 * (CLIP-style), so a search can find photos by describing them.
 */
interface ImageEmbedder {
    /** False while there is no model to do the work; then nothing is scanned and searches skip this step. */
    val isAvailable: Boolean

    suspend fun embed(mediaId: Long): FloatArray?
    suspend fun embedText(text: String): FloatArray?
}
