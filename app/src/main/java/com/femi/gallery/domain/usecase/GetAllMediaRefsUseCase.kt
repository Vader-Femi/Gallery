package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.model.MediaRef
import com.femi.gallery.domain.repository.MediaRepository
import javax.inject.Inject

/** Every photo and video (optionally of one album), used by "Select all". */
class GetAllMediaRefsUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(bucketId: Long? = null): List<MediaRef> = repository.getAllRefs(bucketId)
}
