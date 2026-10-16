package com.femi.gallery.domain.search

import com.femi.gallery.domain.model.MediaType

/** A parsed search: every condition must match (they are ANDed together). */
data class SearchCriteria(
    /** Words that must appear in the file name or the folder name. */
    val terms: List<String> = emptyList(),
    /** Epoch-second ranges (inclusive); an item matches if it falls in any of them. */
    val dateRanges: List<LongRange> = emptyList(),
    val mediaType: MediaType? = null,
    val favoritesOnly: Boolean = false,
) {
    val isEmpty: Boolean
        get() = terms.isEmpty() && dateRanges.isEmpty() && mediaType == null && !favoritesOnly

    companion object {
        val None = SearchCriteria()
    }
}
