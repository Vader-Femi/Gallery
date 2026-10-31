package com.femi.gallery.domain.mirror

import com.femi.gallery.domain.model.Album

enum class AlbumSortOption {
    Recency,
    Name,
    Count,
}

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
    fun order(albums: List<AlbumSummary>, sortOption: AlbumSortOption = AlbumSortOption.Recency): List<AlbumSummary> =
        when (sortOption) {
            AlbumSortOption.Recency ->
                albums
                    .sortedByDescending { it.newestDate }
                    .sortedByDescending { it.relativePath == CAMERA_PATH }
            AlbumSortOption.Name ->
                albums
                    .sortedBy { it.name.lowercase() }
                    .sortedByDescending { it.relativePath == CAMERA_PATH }
            AlbumSortOption.Count ->
                albums
                    .sortedByDescending { it.itemCount }
                    .sortedByDescending { it.relativePath == CAMERA_PATH }
        }

    fun orderAlbums(albums: List<Album>, sortOption: AlbumSortOption): List<Album> =
        when (sortOption) {
            AlbumSortOption.Recency ->
                albums
                    .sortedByDescending { it.newestDate }
                    .sortedByDescending { it.relativePath == CAMERA_PATH }
            AlbumSortOption.Name ->
                albums
                    .sortedBy { it.name.lowercase() }
                    .sortedByDescending { it.relativePath == CAMERA_PATH }
            AlbumSortOption.Count ->
                albums
                    .sortedByDescending { it.itemCount }
                    .sortedByDescending { it.relativePath == CAMERA_PATH }
        }
}
