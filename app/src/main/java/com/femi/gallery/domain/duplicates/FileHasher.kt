package com.femi.gallery.domain.duplicates

import com.femi.gallery.domain.model.MediaItem

/** Reads files to tell whether two of them hold the same bytes. */
interface FileHasher {
    /**
     * A fingerprint of the size and a few small pieces of the file (start, middle, end): fast, but two
     * different files can share it, so it only rules files out. Null when the file cannot be read.
     */
    suspend fun partialHash(item: MediaItem): String?

    /** A fingerprint of every byte of the file: equal fingerprints mean equal files. Null when unreadable. */
    suspend fun fullHash(item: MediaItem): String?

    /** Forgets what was remembered about every file whose id is not in [ids] (the files that no longer exist). */
    suspend fun forgetExcept(ids: Set<Long>) {}
}
