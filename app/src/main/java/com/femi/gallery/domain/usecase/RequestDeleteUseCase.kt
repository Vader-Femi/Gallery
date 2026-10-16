package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.model.WriteRequest
import com.femi.gallery.domain.repository.MediaRepository
import javax.inject.Inject

/** Builds the confirmation(s) to permanently delete [uris]; this cannot be undone. */
class RequestDeleteUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(uris: List<String>): List<WriteRequest> =
        repository.createDeleteRequests(uris)
}
