package com.femi.gallery.domain.model

/** A photo or video that was copied into the Locked folder; [originalUri] is still in the gallery. */
data class LockedCopy(
    val lockedId: Long,
    val originalUri: String,
)

/**
 * Copies are in the Locked folder, and [requests] are the dialogs that delete the originals.
 * Request number `i` covers `lockedIds[i * WriteLimits.BATCH_SIZE until (i + 1) * WriteLimits.BATCH_SIZE]`.
 */
class PreparedLock(
    val lockedIds: List<Long>,
    val requests: List<WriteRequest>,
)
