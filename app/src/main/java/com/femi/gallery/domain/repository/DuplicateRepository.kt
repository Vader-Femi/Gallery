package com.femi.gallery.domain.repository

import com.femi.gallery.domain.duplicates.DuplicateGroup
import com.femi.gallery.domain.duplicates.DuplicateProgress
import kotlinx.coroutines.flow.Flow

/** Where a search for duplicate files is in its work. */
sealed interface DuplicateScanState {
    /** Reading the list of files. */
    data object Listing : DuplicateScanState

    /** Comparing files that might be the same. */
    data class Comparing(val progress: DuplicateProgress) : DuplicateScanState

    /** Finished: the groups of identical files, those with the most to gain first. */
    data class Done(val groups: List<DuplicateGroup>) : DuplicateScanState
}

interface DuplicateRepository {
    /** Searches the library once and ends after [DuplicateScanState.Done]; cancelling the collector stops the search. */
    fun scan(): Flow<DuplicateScanState>
}
