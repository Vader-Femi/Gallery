package com.femi.gallery.domain.face

interface PersonIndex {
    /** Returns the media IDs of photos that include any person matching the query name. */
    suspend fun idsMatching(term: String): Set<Long>
}
