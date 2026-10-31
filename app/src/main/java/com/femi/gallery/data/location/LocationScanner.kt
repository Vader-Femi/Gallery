package com.femi.gallery.data.location

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.geo.GeoPoint
import com.femi.gallery.domain.geo.LocationEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

/** The scan cannot continue because the app may not read the location in photos or videos. */
class LocationPermissionException(cause: Throwable) : Exception(cause)

/**
 * Reads where photos and videos were taken from their EXIF data or ISO 6709 video metadata.
 * Items are read a few at a time, in batches, so progress can be reported and saved as it goes.
 */
@Singleton
class LocationScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * Reads the media items [ids] (with their [modified] times) and passes each finished batch to
     * [onBatch] together with how many items the batch covered. Items whose file has gone are left
     * out of the entries so that they are tried again later.
     *
     * @throws LocationPermissionException if Android refuses to hand over the unredacted files
     */
    suspend fun scan(
        ids: LongArray,
        modified: LongArray,
        onBatch: suspend (entries: List<LocationEntry>, covered: Int) -> Unit,
    ) = coroutineScope {
        val gate = Semaphore(PARALLEL_READS)
        var start = 0
        while (start < ids.size) {
            val end = minOf(ids.size, start + BATCH_SIZE)
            val entries = (start until end)
                .map { i -> async(io) { gate.withPermit { read(ids[i], modified[i]) } } }
                .awaitAll()
                .filterNotNull()
            onBatch(entries, end - start)
            start = end
        }
    }

    /** The item's entry, or null if its file could not be found (it will be tried again). */
    private fun read(id: Long, modified: Long): LocationEntry? {
        val fileUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
        return try {
            // setRequireOriginal asks for the file with its GPS data; Android refuses (with a
            // SecurityException) unless the user allowed ACCESS_MEDIA_LOCATION.
            val origUri = MediaStore.setRequireOriginal(fileUri)
            val latLong = context.contentResolver.openInputStream(origUri)?.use { stream ->
                ExifInterface(stream).latLong
            }
            val point = if (latLong != null) {
                GeoPoint.fromExif(latLong[0], latLong[1])
            } else {
                readVideoLocation(context, origUri)
            }
            LocationEntry(id, modified, point)
        } catch (e: SecurityException) {
            throw LocationPermissionException(e)
        } catch (e: FileNotFoundException) {
            null
        } catch (e: Exception) {
            // A file that cannot be parsed has no place we can use; do not try it again until it changes.
            LocationEntry(id, modified, null)
        }
    }

    private fun readVideoLocation(context: Context, uri: Uri): GeoPoint? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val locationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            retriever.release()
            parseIso6709Location(locationStr)
        } catch (_: Exception) {
            try { retriever.release() } catch (_: Exception) {}
            null
        }
    }

    private companion object {
        const val PARALLEL_READS = 3
        const val BATCH_SIZE = 96

        fun parseIso6709Location(location: String?): GeoPoint? {
            if (location.isNullOrEmpty()) return null
            val regex = Regex("([+-]\\d+\\.\\d+)([+-]\\d+\\.\\d+)")
            val match = regex.find(location) ?: return null
            val lat = match.groupValues[1].toDoubleOrNull() ?: return null
            val lon = match.groupValues[2].toDoubleOrNull() ?: return null
            return GeoPoint.fromExif(lat, lon)
        }
    }
}
