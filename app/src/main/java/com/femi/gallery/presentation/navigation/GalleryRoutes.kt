package com.femi.gallery.presentation.navigation

object GalleryRoutes {
    /** Marks "the whole library" in the viewer's optional bucketId argument. */
    const val NO_BUCKET = Long.MIN_VALUE

    const val ALBUM = "album/{bucketId}"
    const val VIEWER = "viewer/{index}?bucketId={bucketId}"

    fun album(bucketId: Long) = "album/$bucketId"

    /** Opens the viewer at [mediaIndex] within the library, or within one album if [bucketId] is set. */
    fun viewer(mediaIndex: Int, bucketId: Long = NO_BUCKET) = "viewer/$mediaIndex?bucketId=$bucketId"
}
