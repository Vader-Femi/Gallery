package com.femi.gallery.data.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaType
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
     * MediaProvider notifies on the per-type tables (images/video), not reliably on the generic
     * files table, so we watch all of them.
     */
    val observedUris: List<Uri> = listOf(
        collectionUri,
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
    )

    /** Increases whenever anything in the volume's MediaStore changes. */
    fun generation(): Long = MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /** Total number of photos + videos (trashed and pending items are excluded by default). */
    suspend fun count(): Int = withContext(io) {
        resolver.query(collectionUri, arrayOf(MediaStore.Files.FileColumns._ID), queryArgs(), null)
            ?.use { it.count } ?: 0
    }

    suspend fun load(offset: Int, limit: Int): List<MediaItem> = withContext(io) {
        val args = queryArgs().apply {
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

    private fun queryArgs(): Bundle = Bundle().apply {
        putString(
            ContentResolver.QUERY_ARG_SQL_SELECTION,
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)",
        )
        putStringArray(
            ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
            arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            ),
        )
        putStringArray(
            ContentResolver.QUERY_ARG_SORT_COLUMNS,
            arrayOf(MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns._ID),
        )
        putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
    }

    private class Columns(cursor: Cursor) {
        val id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val mediaType = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val mimeType = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        val dateTaken = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
        val dateAdded = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val width = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
        val height = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
        val size = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        val duration = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
        val bucketId = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
        val bucketName = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        val favorite = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
    }

    private fun Cursor.toMediaItem(c: Columns): MediaItem {
        val id = getLong(c.id)
        val isVideo = getInt(c.mediaType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        val base = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        val addedMillis = getLong(c.dateAdded) * 1000L
        return MediaItem(
            id = id,
            uri = ContentUris.withAppendedId(base, id).toString(),
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
            isFavorite = getInt(c.favorite) == 1,
        )
    }

    private companion object {
        val PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.IS_FAVORITE,
        )
    }
}
