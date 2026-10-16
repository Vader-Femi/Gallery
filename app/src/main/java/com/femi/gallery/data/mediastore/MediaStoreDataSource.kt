package com.femi.gallery.data.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.model.Album
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaRef
import com.femi.gallery.domain.model.SpecialAlbums
import com.femi.gallery.domain.model.MediaType
import com.femi.gallery.domain.search.SearchCriteria
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    val collectionUri: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    /**
     * MediaProvider notifies on volume-specific URIs ("external" vs "external_primary"), and
     * camera apps often use a different one from the apps that save via MediaStore. Watching
     * the whole provider authority catches every one of them; the only cost is an occasional
     * extra refresh when non-photo media changes.
     */
    val observedUris: List<Uri> = listOf(MediaStore.AUTHORITY_URI)

    /** Increases whenever anything in the volume's MediaStore changes. */
    fun generation(): Long = MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /**
     * Total number of photos + videos (trashed and pending items are excluded by default),
     * optionally limited to one album ([bucketId]).
     */
    suspend fun count(bucketId: Long? = null, search: SearchCriteria? = null): Int = withContext(io) {
        resolver.query(
            collectionUri,
            arrayOf(MediaStore.Files.FileColumns._ID),
            queryArgs(sorted = false, bucketId = bucketId, search = search),
            null,
        )?.use { it.count } ?: 0
    }

    /** DATE_ADDED (epoch seconds) of every photo and video, newest first. Used to build day sections. */
    suspend fun loadDatesAdded(bucketId: Long? = null, search: SearchCriteria? = null): LongArray = withContext(io) {
        resolver.query(
            collectionUri,
            arrayOf(MediaStore.MediaColumns.DATE_ADDED),
            queryArgs(sorted = true, bucketId = bucketId, search = search),
            null,
        )?.use { cursor ->
            val result = LongArray(cursor.count)
            val column = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            var i = 0
            while (cursor.moveToNext() && i < result.size) result[i++] = cursor.getLong(column)
            result
        } ?: LongArray(0)
    }

    suspend fun load(
        offset: Int,
        limit: Int,
        bucketId: Long? = null,
        search: SearchCriteria? = null,
    ): List<MediaItem> = withContext(io) {
        val args = queryArgs(sorted = true, bucketId = bucketId, search = search).apply {
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        resolver.query(collectionUri, PROJECTION, args, null)?.use { cursor ->
            buildList(cursor.count) {
                val columns = Columns(cursor)
                while (cursor.moveToNext()) add(cursor.toMediaItem(columns))
            }
        } ?: emptyList()
    }

    /**
     * Device folders ("buckets") with their newest item as the cover and an item count.
     * The Camera folder is pinned first, the rest are ordered by most recent activity.
     */
    suspend fun loadAlbums(): List<Album> = withContext(io) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val folders = resolver.query(collectionUri, projection, queryArgs(sorted = true), null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

            // Rows arrive newest first, so the first row seen for a bucket is its cover.
            val covers = LinkedHashMap<Long, Album>()
            val counts = HashMap<Long, Int>()
            while (cursor.moveToNext()) {
                val bucketId = cursor.getLong(bucketColumn)
                counts[bucketId] = (counts[bucketId] ?: 0) + 1
                if (bucketId !in covers) {
                    val isVideo = cursor.getInt(typeColumn) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    covers[bucketId] = Album(
                        bucketId = bucketId,
                        name = cursor.getString(nameColumn).orEmpty(),
                        relativePath = cursor.getString(pathColumn).orEmpty(),
                        coverUri = contentUri(cursor.getLong(idColumn), isVideo).toString(),
                        itemCount = 0,
                    )
                }
            }
            covers.values
                .map { it.copy(itemCount = counts[it.bucketId] ?: 0) }
                .sortedByDescending { it.relativePath == CAMERA_PATH } // stable: keeps recency order otherwise
        } ?: emptyList()

        // Favorites first and the recycle bin last; both only appear when they have something in them.
        listOfNotNull(loadCollection(SpecialAlbums.FAVORITES)) + folders + listOfNotNull(loadCollection(SpecialAlbums.TRASH))
    }

    /** A virtual album ([SpecialAlbums]) with its newest item as cover, or null when empty. */
    private fun loadCollection(id: Long): Album? {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE)
        return resolver.query(collectionUri, projection, queryArgs(sorted = true, bucketId = id), null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                val isVideo = cursor.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                Album(
                    bucketId = id,
                    name = "",
                    relativePath = "",
                    coverUri = contentUri(cursor.getLong(0), isVideo).toString(),
                    itemCount = cursor.count,
                )
            }
        }
    }

    suspend fun loadRefs(bucketId: Long? = null, search: SearchCriteria? = null): List<MediaRef> = withContext(io) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.IS_FAVORITE,
        )
        resolver.query(collectionUri, projection, queryArgs(sorted = true, bucketId = bucketId, search = search), null)
            ?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val favoriteColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
                buildList(cursor.count) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val isVideo = cursor.getInt(typeColumn) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                        add(MediaRef(id, contentUri(id, isVideo).toString(), cursor.getInt(favoriteColumn) == 1))
                    }
                }
            } ?: emptyList()
    }

    /**
     * Each request is a system dialog ("Allow Gallery to move 12 items to trash?"). Very large
     * selections are split because a single request has to fit in one binder transaction.
     */
    suspend fun createTrashRequests(uris: List<String>, trash: Boolean): List<IntentSender> = withContext(io) {
        uris.chunked(WRITE_BATCH_SIZE).map { chunk ->
            MediaStore.createTrashRequest(resolver, chunk.map(Uri::parse), trash).intentSender
        }
    }

    suspend fun createFavoriteRequests(uris: List<String>, favorite: Boolean): List<IntentSender> = withContext(io) {
        uris.chunked(WRITE_BATCH_SIZE).map { chunk ->
            MediaStore.createFavoriteRequest(resolver, chunk.map(Uri::parse), favorite).intentSender
        }
    }

    /** Adds the conditions of [search] to a query; every condition must match. */
    private fun appendSearch(search: SearchCriteria, selection: StringBuilder, args: MutableList<String>) {
        search.terms.forEach { term ->
            selection.append(
                " AND (${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} LIKE ?)",
            )
            args.add("%$term%")
            args.add("%$term%")
        }
        if (search.dateRanges.isNotEmpty()) {
            val column = MediaStore.MediaColumns.DATE_ADDED
            selection.append(
                search.dateRanges.joinToString(separator = " OR ", prefix = " AND (", postfix = ")") {
                    "($column >= ? AND $column <= ?)"
                },
            )
            search.dateRanges.forEach { range ->
                args.add(range.first.toString())
                args.add(range.last.toString())
            }
        }
        search.mediaType?.let { type ->
            selection.append(" AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?")
            args.add(
                when (type) {
                    MediaType.Image -> MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                    MediaType.Video -> MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                }.toString(),
            )
        }
        if (search.favoritesOnly) selection.append(" AND ${MediaStore.MediaColumns.IS_FAVORITE} = 1")
    }

    suspend fun createDeleteRequests(uris: List<String>): List<IntentSender> = withContext(io) {
        uris.chunked(WRITE_BATCH_SIZE).map { chunk ->
            MediaStore.createDeleteRequest(resolver, chunk.map(Uri::parse)).intentSender
        }
    }

    private fun contentUri(id: Long, isVideo: Boolean): Uri {
        val base = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        return ContentUris.withAppendedId(base, id)
    }

    private fun queryArgs(
        sorted: Boolean,
        bucketId: Long? = null,
        search: SearchCriteria? = null,
    ): Bundle = Bundle().apply {
        val selection = StringBuilder("${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)")
        val selectionArgs = mutableListOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        when (bucketId) {
            null -> Unit
            SpecialAlbums.FAVORITES -> selection.append(" AND ${MediaStore.MediaColumns.IS_FAVORITE} = 1")
            // Trashed items are hidden from normal queries; this asks for only those.
            SpecialAlbums.TRASH -> putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            else -> {
                selection.append(" AND ${MediaStore.MediaColumns.BUCKET_ID} = ?")
                selectionArgs.add(bucketId.toString())
            }
        }
        if (search != null) appendSearch(search, selection, selectionArgs)
        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection.toString())
        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs.toTypedArray())
        if (sorted) {
            // DATE_TAKEN is NULL for many files (WhatsApp images, screenshots, downloads), which breaks
            // ordering. DATE_ADDED is always set and matches the capture time for camera photos.
            // MediaProvider rejects expressions like COALESCE in sort order, so a perfect
            // "date taken, else modified" ordering will come later from our own index.
            // Note: QUERY_ARG_SORT_COLUMNS/DIRECTION are ignored here (results came back oldest
            // first), so we pass a plain SQL sort clause instead.
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.MediaColumns._ID} DESC",
            )
        }
    }

    private class Columns(cursor: Cursor) {
        val id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val mediaType = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val displayName = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val mimeType = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        val dateTaken = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
        val dateAdded = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val width = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
        val height = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
        val size = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        val duration = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
        val bucketId = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
        val bucketName = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        val relativePath = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
        val favorite = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
    }

    private fun Cursor.toMediaItem(c: Columns): MediaItem {
        val id = getLong(c.id)
        val isVideo = getInt(c.mediaType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        val addedMillis = getLong(c.dateAdded) * 1000L
        return MediaItem(
            id = id,
            uri = contentUri(id, isVideo).toString(),
            displayName = getString(c.displayName).orEmpty(),
            mimeType = getString(c.mimeType).orEmpty(),
            type = if (isVideo) MediaType.Video else MediaType.Image,
            dateTakenMillis = getLong(c.dateTaken).takeIf { it > 0 } ?: addedMillis,
            dateAddedMillis = addedMillis,
            width = getInt(c.width),
            height = getInt(c.height),
            sizeBytes = getLong(c.size),
            durationMillis = if (isVideo) getLong(c.duration) else 0L,
            bucketId = getLong(c.bucketId),
            bucketName = getString(c.bucketName).orEmpty(),
            relativePath = getString(c.relativePath).orEmpty(),
            isFavorite = getInt(c.favorite) == 1,
        )
    }

    private companion object {
        const val CAMERA_PATH = "DCIM/Camera/"
        const val WRITE_BATCH_SIZE = 500

        val PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.IS_FAVORITE,
        )
    }
}
