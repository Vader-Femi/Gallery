package com.femi.gallery.domain.model

/** A device folder ("bucket") containing photos and/or videos. */
data class Album(
    val bucketId: Long,
    /** Folder name; empty for media stored in the storage root. */
    val name: String,
    /** Folder relative to storage root, e.g. "DCIM/Camera/". */
    val relativePath: String,
    /** content:// URI of the newest item, used as the album cover. */
    val coverUri: String,
    val itemCount: Int,
)
