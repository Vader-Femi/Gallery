package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.model.WriteRequest
import com.femi.gallery.domain.repository.MediaRepository
import javax.inject.Inject

/** Builds the system dialogs that allow the app to move [uris]. */
class RequestWriteAccessUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(uris: List<String>): List<WriteRequest> = repository.createWriteRequests(uris)
}

/** Moves items (whose write access was already approved) into an album folder. */
class MoveMediaUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(uris: List<String>, relativePath: String): Int =
        repository.moveToFolder(uris, relativePath)
}

/** Copies items into an album folder. */
class CopyMediaUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(uris: List<String>, relativePath: String): Int =
        repository.copyToFolder(uris, relativePath)
}
