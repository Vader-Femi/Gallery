package com.femi.gallery.domain.similar

import com.femi.gallery.domain.model.MediaItem

/** Works out the [DifferenceHash] of photos; what it learned is kept, so a photo is looked at only once. */
interface PerceptualHasher {
    /** The hash of the photo, or null when it cannot be read or is too flat to have one. */
    suspend fun hash(item: MediaItem): Long?

    /** Forgets what was learned about every photo whose id is not in [ids] (the files that no longer exist). */
    suspend fun forgetExcept(ids: Set<Long>) {}
}
