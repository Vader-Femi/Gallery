package com.femi.gallery.presentation.navigation

import android.net.Uri
import com.femi.gallery.domain.model.MediaPositions

object GalleryRoutes {
    /** Marks "the whole library" in the viewer's optional bucketId argument. */
    const val NO_BUCKET = Long.MIN_VALUE

    /** Marks "no photo id" in the viewer's optional mediaId argument. */
    const val NO_MEDIA_ID = MediaPositions.NO_ID

    /** Saved-state keys the viewer uses to tell the screen behind it which photo it ended on. */
    const val VIEWED_INDEX_KEY = "viewedMediaIndex"
    const val VIEWED_ID_KEY = "viewedMediaId"

    const val ALBUM = "album/{bucketId}"
    const val SEARCH = "search"
    const val SEARCH_QUERY = "search?query={query}"
    const val MAP = "map"
    const val STORIES = "stories"
    const val VIDEOS = "videos"
    const val PLACE = "place?query={query}"
    const val STORY = "story/{fromDay}/{toDay}"
    const val SLIDESHOW = "slideshow?query={query}&bucketId={bucketId}"
    const val EDITOR = "editor?uri={uri}"
    const val VIEWER = "viewer/{index}?mediaId={mediaId}&bucketId={bucketId}&query={query}"

    fun album(bucketId: Long) = "album/$bucketId"

    fun search(query: String = ""): String = if (query.isBlank()) SEARCH else "search?query=${Uri.encode(query)}"

    /** A story covering the local days [fromDay]..[toDay] (`LocalDate.toEpochDay()`). */
    fun story(fromDay: Long, toDay: Long) = "story/$fromDay/$toDay"

    /** The photos taken inside a map area; [query] is the area as a search query (`in:...`). */
    fun place(query: String) = "place?query=${Uri.encode(query)}"

    /** A slideshow of the photos a search [query] describes, or of one album ([bucketId]). */
    fun slideshow(query: String = "", bucketId: Long = NO_BUCKET) = "slideshow?query=${Uri.encode(query)}&bucketId=$bucketId"

    /** The photo editor for the photo at content [uri]. */
    fun editor(uri: String) = "editor?uri=${Uri.encode(uri)}"

    /**
     * Opens the viewer at [mediaIndex] within the library, within one album if [bucketId] is set,
     * or within the results of a search if [query] is set. [mediaId] is the tapped photo, so the
     * viewer can still open on it if the list shifted since the grid loaded it.
     */
    fun viewer(mediaIndex: Int, mediaId: Long, bucketId: Long = NO_BUCKET, query: String? = null): String {
        val base = "viewer/$mediaIndex?mediaId=$mediaId&bucketId=$bucketId"
        return if (query.isNullOrBlank()) base else "$base&query=${Uri.encode(query)}"
    }
}
