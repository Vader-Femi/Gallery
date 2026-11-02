package com.femi.gallery.domain.model

/**
 * Albums the person chose to hide. A hidden album's photos and videos are left out of everything that is not that
 * album itself: the timeline, search, stories, the map, favorites, the recycle bin, slideshows and the duplicates
 * finder. Only the album list (so the album can be un-hidden in Settings) still shows it.
 */
object HiddenAlbums {
    /**
     * The albums to leave out of a request for [bucketId]. Asking for one real folder by its id returns that folder
     * as it is (nothing links to a hidden one, but a direct request must not come back empty); every other request,
     * including the special albums (favorites, recycle bin), leaves the hidden ones out.
     */
    fun excludedFor(bucketId: Long?, hidden: Set<Long>): Set<Long> =
        if (bucketId == null || bucketId == SpecialAlbums.FAVORITES || bucketId == SpecialAlbums.TRASH) hidden else emptySet()
}
