package com.femi.gallery.data.location

import android.content.ContentUris
import android.content.Context
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

/** The scan cannot continue because the app may not read the location in photos. */
class LocationPermissionException(cause: Throwable) : Exception(cause)

/**
 * Reads where photos were taken from their EXIF data. Photos are read a few at a time, in batches,
 * so progress can be reported and saved as it goes.
 */
@Singleton
class LocationScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * Reads the photos [ids] (with their [modified] times) and passes each finished batch to
     * [onBatch] together with how many photos the batch covered. Photos whose file has gone are left
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

    /** The photo's entry, or null if its file could not be found (it will be tried again). */
    private fun read(id: Long, modified: Long): LocationEntry? {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        return try {
            // setRequireOriginal asks for the file with its GPS data; Android refuses (with a
            // SecurityException) unless the user allowed ACCESS_MEDIA_LOCATION.
            val latLong = context.contentResolver.openInputStream(MediaStore.setRequireOriginal(uri))?.use { stream ->
                ExifInterface(stream).latLong
            }
            LocationEntry(id, modified, latLong?.let { GeoPoint.fromExif(it[0], it[1]) })
        } catch (e: SecurityException) {
            throw LocationPermissionException(e)
        } catch (e: FileNotFoundException) {
            null
        } catch (e: Exception) {
            // A file that cannot be parsed has no place we can use; do not try it again until it changes.
            LocationEntry(id, modified, null)
        }
    }

    private companion object {
        const val PARALLEL_READS = 3
        const val BATCH_SIZE = 96
    }
}
