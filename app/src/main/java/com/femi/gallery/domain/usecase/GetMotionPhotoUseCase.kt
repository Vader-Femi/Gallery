package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MotionPhotoInfo
import com.femi.gallery.domain.repository.MotionPhotoRepository
import javax.inject.Inject

class GetMotionPhotoUseCase @Inject constructor(
    private val repository: MotionPhotoRepository,
) {
    suspend operator fun invoke(item: MediaItem): MotionPhotoInfo? = repository.getMotionPhoto(item)
}
