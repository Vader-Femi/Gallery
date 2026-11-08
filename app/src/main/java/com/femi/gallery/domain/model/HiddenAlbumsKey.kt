package com.femi.gallery.domain.model

/**
 * A number that stands for the set of hidden albums, so that things built from the photos that show (the stories)
 * know when to be built again: the same albums always give the same number, whatever their order, and any other set
 * gives another number (in practice: it is a 64-bit hash). No hidden album gives 0.
 */
object HiddenAlbumsKey {
    private const val OFFSET_BASIS = -3750763034362895579L // FNV-1a, 64 bit
    private const val PRIME = 1099511628211L

    fun of(hiddenBucketIds: Collection<Long>): Long {
        if (hiddenBucketIds.isEmpty()) return 0L
        var hash = OFFSET_BASIS
        for (id in hiddenBucketIds.toSortedSet()) {
            for (shift in 0 until 64 step 8) {
                hash = (hash xor ((id ushr shift) and 0xFF)) * PRIME
            }
        }
        return if (hash == 0L) 1L else hash
    }
}
