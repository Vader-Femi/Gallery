package com.femi.gallery.domain.model

import com.femi.gallery.domain.search.SearchCriteria

/** What another app asked to pick: photos, videos or either. */
enum class PickerFilter {
    All,
    ImagesOnly,
    VideosOnly;

    /** The search that limits the library to this filter, or null to show everything. */
    fun toCriteria(): SearchCriteria? = when (this) {
        All -> null
        ImagesOnly -> SearchCriteria(mediaType = MediaType.Image)
        VideosOnly -> SearchCriteria(mediaType = MediaType.Video)
    }

    companion object {
        /**
         * Reads the MIME types of a pick request (image types, video types, or any type). Only
         * when every type is an image type, or every one is a video type, is the picker narrowed.
         */
        fun fromMimeTypes(mimeTypes: List<String>): PickerFilter {
            val types = mimeTypes.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            return when {
                types.isEmpty() -> All
                types.all { it.startsWith("image/") } -> ImagesOnly
                types.all { it.startsWith("video/") } -> VideosOnly
                else -> All
            }
        }
    }
}
