package com.femi.gallery.data.similar

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Size
import com.femi.gallery.data.db.PhotoHashDao
import com.femi.gallery.data.db.PhotoHashRow
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.similar.DifferenceHash
import com.femi.gallery.domain.similar.PerceptualHasher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hashes photos from their small thumbnails (a 64 by 64 picture is plenty for a 9 by 8 grid), and keeps what it
 * learned in the index database, so that the next search only looks at photos that are new or were edited.
 */
@Singleton
class ThumbnailPerceptualHasher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    private val dao: PhotoHashDao,
) : PerceptualHasher {

    /** What is in the database, read once and kept in step with it; null until the first photo is asked about. */
    private var remembered: MutableMap<Long, PhotoHashRow>? = null
    private val loading = Mutex()

    /** Rows made but not yet written; written in groups, not one transaction per photo. */
    private val pending = ArrayList<PhotoHashRow>()
    private val pendingLock = Any()

    override suspend fun hash(item: MediaItem): Long? = withContext(io) {
        if (item.isVideo) return@withContext null
        val known = rows()
        known[item.id]?.takeIf { it.size == item.sizeBytes && it.dateAdded == item.dateAddedMillis }?.let { return@withContext it.hash }

        val computed = try {
            val bitmap = context.contentResolver.loadThumbnail(Uri.parse(item.uri), Size(THUMBNAIL_SIDE, THUMBNAIL_SIDE), null)
            try {
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                val luma = IntArray(pixels.size) { DifferenceHash.luma(pixels[it]) }
                Result.success(DifferenceHash.fromLuma(luma, width, height))
            } finally {
                bitmap.recycle()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read a thumbnail of photo ${item.id}", e)
            Result.failure(e)
        }
        // A photo that could not be read is not remembered: it may work next time.
        val hash = computed.getOrElse { return@withContext null }
        val row = PhotoHashRow(item.id, item.sizeBytes, item.dateAddedMillis, hash)
        synchronized(known) { known[item.id] = row }
        val full = synchronized(pendingLock) {
            pending.add(row)
            if (pending.size >= WRITE_EVERY) ArrayList(pending).also { pending.clear() } else null
        }
        full?.let { dao.upsertAll(it) }
        hash
    }

    /** Writes the rows still waiting; called when a search ends or is cancelled. */
    suspend fun flush() = withContext(io) {
        val rest = synchronized(pendingLock) { ArrayList(pending).also { pending.clear() } }
        if (rest.isNotEmpty()) dao.upsertAll(rest)
    }

    override suspend fun forgetExcept(ids: Set<Long>) = withContext(io) {
        val known = rows()
        val gone = synchronized(known) { known.keys.filter { it !in ids } }
        if (gone.isNotEmpty()) {
            synchronized(known) { gone.forEach(known::remove) }
            dao.deleteAll(gone)
        }
    }

    private suspend fun rows(): MutableMap<Long, PhotoHashRow> {
        remembered?.let { return it }
        return loading.withLock {
            remembered ?: java.util.Collections.synchronizedMap(HashMap<Long, PhotoHashRow>().apply {
                dao.all().forEach { put(it.id, it) }
            }).also { remembered = it }
        }
    }

    private companion object {
        const val TAG = "SimilarScan"
        const val THUMBNAIL_SIDE = 64
        const val WRITE_EVERY = 100
    }
}
