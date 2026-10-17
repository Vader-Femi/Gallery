package com.femi.gallery.data.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.model.Album
import com.femi.gallery.domain.model.AlbumPaths
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaOrder
import com.femi.gallery.domain.model.MediaOrdering
import com.femi.gallery.domain.model.MediaRef
import com.femi.gallery.domain.model.SpecialAlbums
import com.femi.gallery.domain.model.WriteLimits
import com.femi.gallery.domain.model.MediaType
import com.femi.gallery.domain.search.SearchCriteria
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
     * Ids and dates of everything matching the filters, newest first. A photo's date is when it
     * was taken, or when it was added if the file has no "date taken" (WhatsApp images,
     * screenshots, ...). MediaProvider cannot sort by "one column, else another", so the ordering
     * is done here; only two numbers per item are read, which is cheap even for 50,000 photos.
     */
    suspend fun loadOrder(bucketId: Long? = null, search: SearchCriteria? = null): MediaOrder = withContext(io) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        resolver.query(collectionUri, projection, queryArgs(bucketId = bucketId, search = search), null)?.use { cursor ->
            val ids = LongArray(cursor.count)
            val dates = LongArray(cursor.count)
            var i = 0
            while (cursor.moveToNext() && i < ids.size) {
                ids[i] = cursor.getLong(0)
                dates[i] = MediaOrdering.effectiveDateMillis(taken = cursor.getLong(1), addedSeconds = cursor.getLong(2))
                i++
            }
            MediaOrdering.newestFirst(ids, dates, i)
        } ?: MediaOrder.Empty
    }

    /** Dates (epoch seconds) of the matching items, newest first. Used to build day sections. */
    suspend fun loadDatesSeconds(bucketId: Long? = null, search: SearchCriteria? = null): LongArray {
        val order = loadOrder(bucketId, search)
        return LongArray(order.size) { order.datesMillis[it] / 1000 }
    }

    /**
     * Loads the full rows for [ids] (one page of an ordered list) and returns them in that same
     * order. Items that no longer exist or match are skipped.
     */
    suspend fun loadByIds(
        ids: LongArray,
        bucketId: Long? = null,
        search: SearchCriteria? = null,
    ): List<MediaItem> = withContext(io) {
        if (ids.isEmpty()) return@withContext emptyList()
        val byId = resolver.query(
            collectionUri,
            PROJECTION,
            queryArgs(bucketId = bucketId, search = search, onlyIds = ids),
            null,
        )?.use { cursor ->
            val columns = Columns(cursor)
            buildMap(cursor.count) {
                while (cursor.moveToNext()) {
                    val item = cursor.toMediaItem(columns)
                    put(item.id, item)
                }
            }
        }.orEmpty()
        ids.map { byId[it] }.filterNotNull()
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
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        val folders = resolver.query(collectionUri, projection, queryArgs(), null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

            // Rows arrive in no particular order, so the cover is the item with the newest date.
            val albumsMap = HashMap<Long, AlbumBuilder>()
            while (cursor.moveToNext()) {
                val bucketId = cursor.getLong(bucketColumn)
                val date = MediaOrdering.effectiveDateMillis(cursor.getLong(takenColumn), cursor.getLong(addedColumn))
                val isVideo = cursor.getInt(typeColumn) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val coverUri = contentUri(cursor.getLong(idColumn), isVideo).toString()
                val existing = albumsMap[bucketId]
                if (existing == null) {
                    albumsMap[bucketId] = AlbumBuilder(
                        bucketId = bucketId,
                        name = cursor.getString(nameColumn).orEmpty(),
                        relativePath = cursor.getString(pathColumn).orEmpty(),
                        coverUri = coverUri,
                        newestDate = date,
                        itemCount = 1,
                    )
                } else {
                    existing.itemCount++
                    if (date > existing.newestDate) {
                        existing.newestDate = date
                        existing.coverUri = coverUri
                    }
                }
            }

            albumsMap.values
                .sortedByDescending { it.newestDate }
                .map { builder ->
                    Album(
                        bucketId = builder.bucketId,
                        name = builder.name,
                        relativePath = builder.relativePath,
                        coverUri = builder.coverUri,
                        itemCount = builder.itemCount,
                    )
                }
                .sortedByDescending { it.relativePath == CAMERA_PATH } // stable: keeps recency order otherwise
        } ?: emptyList()

        // Favorites first and the recycle bin last; both only appear when they have something in them.
        listOfNotNull(loadCollection(SpecialAlbums.FAVORITES)) + folders + listOfNotNull(loadCollection(SpecialAlbums.TRASH))
    }

    suspend fun loadAlbumName(bucketId: Long): String = withContext(io) {
        val projection = arrayOf(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        resolver.query(collectionUri, projection, queryArgs(bucketId = bucketId), null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else ""
        }.orEmpty()
    }

    private class AlbumBuilder(
        val bucketId: Long,
        val name: String,
        val relativePath: String,
        var coverUri: String,
        var newestDate: Long,
        var itemCount: Int,
    )

    /** A virtual album ([SpecialAlbums]) with its newest item as cover, or null when empty. */
    private suspend fun loadCollection(id: Long): Album? {
        val order = loadOrder(bucketId = id)
        if (order.size == 0) return null
        val cover = loadByIds(longArrayOf(order.ids.first()), bucketId = id).firstOrNull() ?: return null
        return Album(
            bucketId = id,
            name = "",
            relativePath = "",
            coverUri = cover.uri,
            itemCount = order.size,
        )
    }

    suspend fun loadRefs(bucketId: Long? = null, search: SearchCriteria? = null): List<MediaRef> = withContext(io) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.IS_FAVORITE,
        )
        resolver.query(collectionUri, projection, queryArgs(bucketId = bucketId, search = search), null)
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
        uris.chunked(WriteLimits.BATCH_SIZE).map { chunk ->
            MediaStore.createTrashRequest(resolver, chunk.map(Uri::parse), trash).intentSender
        }
    }

    suspend fun createFavoriteRequests(uris: List<String>, favorite: Boolean): List<IntentSender> = withContext(io) {
        uris.chunked(WriteLimits.BATCH_SIZE).map { chunk ->
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
            // Same date the grid shows: date taken, else date added (seconds, hence the *1000).
            val taken = MediaStore.MediaColumns.DATE_TAKEN
            val added = MediaStore.MediaColumns.DATE_ADDED
            selection.append(
                search.dateRanges.joinToString(separator = " OR ", prefix = " AND (", postfix = ")") {
                    "(($taken >= ? AND $taken <= ?) OR (($taken IS NULL OR $taken = 0) AND $added >= ? AND $added <= ?))"
                },
            )
            search.dateRanges.forEach { range ->
                args.add((range.first * 1000L).toString())
                args.add((range.last * 1000L + 999L).toString())
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
        uris.chunked(WriteLimits.BATCH_SIZE).map { chunk ->
            MediaStore.createDeleteRequest(resolver, chunk.map(Uri::parse)).intentSender
        }
    }

    suspend fun createWriteRequests(uris: List<String>): List<IntentSender> = withContext(io) {
        uris.chunked(WriteLimits.BATCH_SIZE).map { chunk ->
            MediaStore.createWriteRequest(resolver, chunk.map(Uri::parse)).intentSender
        }
    }

    /**
     * Moves each item by changing its folder. Only works for items the user approved through
     * [createWriteRequests]; items that fail (e.g. a protected folder) are skipped.
     */
    suspend fun moveItems(uris: List<String>, relativePath: String): Int = withContext(io) {
        uris.map { uri ->
            async {
                runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, AlbumPaths.forMedia(relativePath, isVideo = isVideoUri(uri)))
                    }
                    resolver.update(Uri.parse(uri), values, null, null) > 0
                }.getOrDefault(false)
            }
        }.awaitAll().count { it }
    }

    /** Copies each item into a new entry in the album folder; the original is untouched. */
    suspend fun copyItems(uris: List<String>, relativePath: String): Int = withContext(io) {
        uris.map { uri ->
            async { runCatching { copyItem(Uri.parse(uri), relativePath) }.getOrDefault(false) }
        }.awaitAll().count { it }
    }

    private fun copyItem(source: Uri, relativePath: String): Boolean {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        // The generic files table has every column, whichever collection the item came from.
        val row = ContentUris.withAppendedId(collectionUri, ContentUris.parseId(source))
        val meta = resolver.query(row, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                CopyMeta(cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getLong(3))
            } else {
                null
            }
        } ?: return false
        val name = meta.name ?: return false
        val mime = meta.mime ?: return false
        val isVideo = mime.startsWith("video/")
        val taken = meta.taken.takeIf { it > 0 } ?: (meta.added * 1000L)

        val target = resolver.insert(
            if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, AlbumPaths.forMedia(relativePath, isVideo))
                put(MediaStore.MediaColumns.DATE_TAKEN, taken)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            },
        ) ?: return false
        return try {
            val input = resolver.openInputStream(source) ?: error("Cannot read $source")
            val output = resolver.openOutputStream(target) ?: error("Cannot write $target")
            input.use { from -> output.use { to -> from.copyTo(to) } }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            false
        }
    }

    private class CopyMeta(val name: String?, val mime: String?, val taken: Long, val added: Long)

    private fun isVideoUri(uri: String): Boolean = uri.contains("/video/")

    private fun contentUri(id: Long, isVideo: Boolean): Uri {
        val base = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        return ContentUris.withAppendedId(base, id)
    }

    private fun queryArgs(
        bucketId: Long? = null,
        search: SearchCriteria? = null,
        /** Restricts the query to these ids (used to load one page of an ordered list). */
        onlyIds: LongArray? = null,
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
        if (onlyIds != null && onlyIds.isNotEmpty()) {
            selection.append(" AND ${MediaStore.MediaColumns._ID} IN (${onlyIds.joinToString(",") { "?" }})")
            onlyIds.forEach { selectionArgs.add(it.toString()) }
        }
        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection.toString())
        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs.toTypedArray())
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
