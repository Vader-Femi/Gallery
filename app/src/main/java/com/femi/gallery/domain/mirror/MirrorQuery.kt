package com.femi.gallery.domain.mirror

import com.femi.gallery.domain.model.MediaType
import com.femi.gallery.domain.model.SpecialAlbums
import com.femi.gallery.domain.search.SearchCriteria

/**
 * A request for part of the library that the local copy of the library (the "mirror", see
 * `MediaMirror`) can answer by itself.
 *
 * It can answer everything that depends only on what it keeps about each item: its album, whether it is a
 * photo or a video, whether it is a favorite, and its date. It cannot answer a search for words (they match
 * the text, labels and places the scans found, through MediaStore) or an area of the map, nor the recycle
 * bin (trashed items are not kept in it); those go to MediaStore as before.
 */
data class MirrorQuery(
    /** Only this album (a real folder), or null for the whole library. */
    val bucketId: Long?,
    val mediaType: MediaType?,
    val favoritesOnly: Boolean,
    /** Epoch-second ranges (inclusive); an item is kept if its date falls in any of them. Empty means any date. */
    val dateRanges: List<LongRange>,
) {
    companion object {
        /** The mirror's version of a request for [bucketId] and [search], or null when MediaStore has to answer it. */
        fun from(bucketId: Long?, search: SearchCriteria?): MirrorQuery? {
            if (bucketId == SpecialAlbums.TRASH || bucketId == SpecialAlbums.LOCKED) return null
            if (search != null && (search.terms.isNotEmpty() || search.area != null)) return null
            val favoritesAlbum = bucketId == SpecialAlbums.FAVORITES
            return MirrorQuery(
                bucketId = if (favoritesAlbum) null else bucketId,
                mediaType = search?.mediaType,
                favoritesOnly = favoritesAlbum || search?.favoritesOnly == true,
                dateRanges = search?.dateRanges.orEmpty(),
            )
        }
    }
}
