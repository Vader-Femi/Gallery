package com.femi.gallery.domain.repository

import com.femi.gallery.domain.duplicates.DuplicateGroup
import kotlinx.coroutines.flow.Flow

/** Where the search for similar photos is in its work. */
sealed interface SimilarScanState {
    /** Reading the list of photos. */
    data object Listing : SimilarScanState

    /** Looking at the photos: [done] of [total]; photos seen before are quick. */
    data class Hashing(val done: Int, val total: Int) : SimilarScanState

    /** Finished: the groups of similar photos, those with the most to gain first. */
    data class Done(val groups: List<DuplicateGroup>) : SimilarScanState
}

interface SimilarPhotoRepository {
    /**
     * Searches the whole library once (photos in hidden albums and in the Locked folder are left out) and ends after
     * [SimilarScanState.Done]; cancelling the collector stops the search.
     */
    fun scan(): Flow<SimilarScanState>
}
