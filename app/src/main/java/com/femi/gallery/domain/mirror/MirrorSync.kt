package com.femi.gallery.domain.mirror

/**
 * Decisions for keeping the local copy of the library in step with MediaStore.
 *
 * MediaStore has a generation number that goes up whenever anything in the volume changes. The mirror
 * remembers the number it was last brought up to date at, so "nothing changed" costs one number to compare.
 */
object MirrorSync {

    enum class Action {
        /** Never copied, or the volume was reset: copy everything (in the background; until then MediaStore answers). */
        Import,

        /** Something changed: copy what changed and drop what is gone. */
        Update,

        /** Nothing changed since the last time. */
        Nothing,
    }

    fun decide(lastGeneration: Long?, generationNow: Long): Action = when {
        lastGeneration == null -> Action.Import
        // The number only ever goes up, so a lower one means the volume was reset (storage wiped, card swapped):
        // what the mirror holds cannot be trusted, and a list of "what changed since" would be empty.
        generationNow < lastGeneration -> Action.Import
        generationNow > lastGeneration -> Action.Update
        else -> Action.Nothing
    }

    /** What to do about items the mirror and MediaStore disagree on. */
    data class Reconciliation(
        /** In the mirror but no longer in MediaStore (deleted or moved to the recycle bin). */
        val toDelete: LongArray,
        /** In MediaStore but not in the mirror (missed, for instance because they are on another storage volume). */
        val missing: LongArray,
    ) {
        val isEmpty: Boolean get() = toDelete.isEmpty() && missing.isEmpty()
    }

    /** Compares the ids MediaStore has now with the ids the mirror holds. */
    fun reconcile(mediaStoreIds: LongArray, mirrorIds: LongArray): Reconciliation {
        val inStore = HashSet<Long>(mediaStoreIds.size * 2).apply { mediaStoreIds.forEach { add(it) } }
        val inMirror = HashSet<Long>(mirrorIds.size * 2).apply { mirrorIds.forEach { add(it) } }
        return Reconciliation(
            toDelete = mirrorIds.filter { it !in inStore }.toLongArray(),
            missing = mediaStoreIds.filter { it !in inMirror }.toLongArray(),
        )
    }
}
