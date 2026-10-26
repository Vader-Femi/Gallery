package com.femi.gallery.domain.repository

import com.femi.gallery.domain.clip.DetectedEmbedding

interface ClipStore {
    suspend fun load(): List<DetectedEmbedding>
    suspend fun save(entries: List<DetectedEmbedding>)
}
