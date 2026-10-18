package com.femi.gallery.presentation.navigation

import android.net.Uri

object GalleryRoutes {
    /** Marks "the whole library" in the viewer's optional bucketId argument. */
    const val NO_BUCKET = Long.MIN_VALUE

    const val ALBUM = "album/{bucketId}"
    const val SEARCH = "search"
    const val STORY = "story/{fromDay}/{toDay}"
    const val SLIDESHOW = "slideshow?query={query}"
    const val EDITOR = "editor?uri={uri}"
    const val VIEWER = "viewer/{index}?bucketId={bucketId}&query={query}"

    fun album(bucketId: Long) = "album/$bucketId"

    /** A story covering the local days [fromDay]..[toDay] (`LocalDate.toEpochDay()`). */
    fun story(fromDay: Long, toDay: Long) = "story/$fromDay/$toDay"

    /** A slideshow of the photos a search [query] describes. */
    fun slideshow(query: String) = "slideshow?query=${Uri.encode(query)}"

    /** The photo editor for the photo at content [uri]. */
    fun editor(uri: String) = "editor?uri=${Uri.encode(uri)}"

    /**
     * Opens the viewer at [mediaIndex] within the library, within one album if [bucketId] is set,
     * or within the results of a search if [query] is set.
     */
    fun viewer(mediaIndex: Int, bucketId: Long = NO_BUCKET, query: String? = null): String {
        val base = "viewer/$mediaIndex?bucketId=$bucketId"
        return if (query.isNullOrBlank()) base else "$base&query=${Uri.encode(query)}"
    }
}
