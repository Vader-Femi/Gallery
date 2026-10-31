package com.femi.gallery.domain.model

/** The minimum needed to act on a photo or video: which file it is and whether it's a favorite. */
data class MediaRef(
    val id: Long,
    val uri: String,
    val isFavorite: Boolean,
    val isVideo: Boolean = uri.contains("/video/"),
)

fun MediaItem.toRef() = MediaRef(id = id, uri = uri, isFavorite = isFavorite, isVideo = isVideo)
