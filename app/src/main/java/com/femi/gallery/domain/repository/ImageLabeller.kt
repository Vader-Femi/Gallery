package com.femi.gallery.domain.repository

interface ImageLabeller {
    suspend fun label(mediaId: Long): List<String>
}
