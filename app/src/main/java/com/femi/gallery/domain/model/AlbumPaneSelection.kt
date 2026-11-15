package com.femi.gallery.domain.model

/**
 * Which album the detail pane of the two-pane Albums screen shows (tablets and unfolded foldables).
 *
 * The pane never opens the Locked folder or People by itself: Locked would raise the fingerprint prompt as soon as
 * the Albums tab opens, and People is not an album. A choice that is no longer in the list (the album was hidden,
 * deleted or emptied since) is dropped, so a hidden album cannot stay on screen.
 */
object AlbumPaneSelection {

    /** Special albums that are only shown when the user picks them. */
    private val NEVER_AUTOMATIC = setOf(SpecialAlbums.LOCKED, SpecialAlbums.PEOPLE)

    private val SPECIAL = setOf(SpecialAlbums.FAVORITES, SpecialAlbums.TRASH, SpecialAlbums.LOCKED, SpecialAlbums.PEOPLE)

    /** The bucket id to show, or null when there is nothing to show. */
    fun resolve(selected: Long?, albums: List<Album>): Long? {
        if (selected != null && selected != SpecialAlbums.PEOPLE && albums.any { it.bucketId == selected }) return selected
        return albums.firstOrNull { it.bucketId !in SPECIAL }?.bucketId
            ?: albums.firstOrNull { it.bucketId !in NEVER_AUTOMATIC }?.bucketId
    }
}
