package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.repository.MediaRepository
import javax.inject.Inject

class GetAlbumNameUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(bucketId: Long): String = repository.getAlbumName(bucketId)
}
