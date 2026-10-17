package com.femi.gallery.domain.model

object WriteLimits {
    /**
     * Largest number of files in one system confirmation dialog. Bigger selections are split, and
     * the same chunking is used when pairing copies with the deletions that must follow them.
     */
    const val BATCH_SIZE = 500
}
