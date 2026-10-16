package com.femi.gallery.domain.model

data class MediaItem(
    val id: Long,
    /** content:// URI as a string, so the domain layer stays free of Android types. */
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val type: MediaType,
    /** Capture time, falling back to the date the file was added when EXIF/date taken is missing. */
    val dateTakenMillis: Long,
    val dateAddedMillis: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    /** Only meaningful for videos. */
    val durationMillis: Long,
    val bucketId: Long,
    val bucketName: String,
    /** Folder relative to storage root, e.g. "DCIM/Camera/". */
    val relativePath: String,
    val isFavorite: Boolean,
) {
    val isVideo: Boolean get() = type == MediaType.Video
}

enum class MediaType { Image, Video }
