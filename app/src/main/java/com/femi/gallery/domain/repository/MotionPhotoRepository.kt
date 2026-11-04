package com.femi.gallery.domain.repository

import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MotionPhotoInfo

interface MotionPhotoRepository {
    suspend fun getMotionPhoto(item: MediaItem): MotionPhotoInfo?
}
