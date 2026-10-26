package com.femi.gallery.domain.repository

interface ImageEmbedder {
    suspend fun embed(mediaId: Long): FloatArray?
    suspend fun embedText(text: String): FloatArray?
}
