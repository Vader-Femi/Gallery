package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.model.MediaDetails
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.repository.MediaRepository
import javax.inject.Inject

class GetMediaDetailsUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(item: MediaItem): MediaDetails = repository.getDetails(item)
}
