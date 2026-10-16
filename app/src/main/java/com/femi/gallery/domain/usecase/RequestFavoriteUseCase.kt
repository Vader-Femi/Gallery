package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.model.WriteRequest
import com.femi.gallery.domain.repository.MediaRepository
import javax.inject.Inject

/** Builds the system confirmation request(s) for [uris]; large selections are split into batches. */
class RequestFavoriteUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(uris: List<String>, favorite: Boolean): List<WriteRequest> =
        repository.createFavoriteRequests(uris, favorite)
}
