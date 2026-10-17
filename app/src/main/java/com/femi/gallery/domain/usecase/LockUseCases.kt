package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.model.PreparedLock
import com.femi.gallery.domain.repository.MediaRepository
import javax.inject.Inject

/**
 * First half of "move to Locked folder": copies the items into private storage and prepares the
 * system dialogs that delete the originals. Nothing is removed from the gallery until the user
 * approves those dialogs.
 */
class LockMediaUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(uris: List<String>): PreparedLock {
        val copies = repository.copyToLocked(uris)
        val requests = repository.createDeleteRequests(copies.map { it.originalUri })
        return PreparedLock(copies.map { it.lockedId }, requests)
    }
}

/** Moves items back out of the Locked folder into the gallery. Returns how many were restored. */
class UnlockMediaUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(ids: List<Long>): Int = repository.unlockItems(ids)
}

/** Permanently deletes items from the Locked folder. */
class DeleteLockedUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(ids: List<Long>) = repository.deleteLockedItems(ids)
}
