package com.femi.gallery.domain.mirror

/** What is known about one album before the list is ordered. */
data class AlbumSummary(
    val bucketId: Long,
    val name: String,
    val relativePath: String,
    val coverUri: String,
    val itemCount: Int,
    /** Date of the newest item (epoch milliseconds). */
    val newestDate: Long,
)

object AlbumSorting {
    /** The camera's own folder. */
    const val CAMERA_PATH = "DCIM/Camera/"

    /** Albums with the most recent activity first, except that the Camera folder is always on top. */
    fun order(albums: List<AlbumSummary>): List<AlbumSummary> =
        albums
            .sortedByDescending { it.newestDate }
            .sortedByDescending { it.relativePath == CAMERA_PATH } // stable: keeps the recency order otherwise
}
