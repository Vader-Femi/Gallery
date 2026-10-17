package com.femi.gallery.domain.model

/**
 * Rules for album folders. An album is a folder under shared storage; Android only lets apps
 * put photos under DCIM or Pictures and videos under DCIM or Movies.
 */
object AlbumPaths {

    private val IMAGE_ROOTS = setOf("DCIM", "Pictures")
    private val VIDEO_ROOTS = setOf("DCIM", "Movies")
    private val FOLDER_ROOTS = setOf("DCIM", "Pictures", "Movies")
    private val FORBIDDEN_CHARACTERS = Regex("[\\\\/:*?\"<>|]")
    private const val MAX_NAME_LENGTH = 60

    /** A safe folder name from what the user typed, or null if nothing usable is left. */
    fun sanitizeName(raw: String): String? =
        raw.replace(FORBIDDEN_CHARACTERS, "")
            .trim()
            .trimStart('.')
            .take(MAX_NAME_LENGTH)
            .trim()
            .takeIf { it.isNotEmpty() }

    /** New albums go under DCIM, the one place both photos and videos are allowed. */
    fun newAlbumPath(name: String): String = "DCIM/$name/"

    /** `Pictures/Trips/` -> `Pictures/`. */
    fun parent(path: String): String {
        val trimmed = path.trimEnd('/')
        val parent = trimmed.substringBeforeLast('/', missingDelimiterValue = "")
        return if (parent.isEmpty()) "" else "$parent/"
    }

    /** `Pictures/Trips/` renamed to `Holiday` -> `Pictures/Holiday/`. */
    fun renamed(path: String, newName: String): String = parent(path) + newName + "/"

    /** A folder directly or deeper under DCIM, Pictures or Movies (never those roots themselves). */
    fun isAlbumFolder(path: String): Boolean {
        val parts = path.trimEnd('/').split('/')
        return parts.size >= 2 && parts.first() in FOLDER_ROOTS
    }

    /** The camera folder keeps its name, since the camera app writes there. */
    fun canRename(path: String): Boolean =
        isAlbumFolder(path) && path.trimEnd('/') != "DCIM/Camera"

    /**
     * The same album folder for a photo or a video: `Pictures/Trips/` is not allowed for videos,
     * so they go to `Movies/Trips/` instead (and the other way round).
     */
    fun forMedia(path: String, isVideo: Boolean): String {
        val allowed = if (isVideo) VIDEO_ROOTS else IMAGE_ROOTS
        val top = path.substringBefore('/')
        return when {
            top in allowed -> path
            top == "Pictures" && isVideo -> "Movies/" + path.substringAfter('/')
            top == "Movies" && !isVideo -> "Pictures/" + path.substringAfter('/')
            else -> path
        }
    }
}
