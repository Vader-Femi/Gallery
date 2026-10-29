package com.femi.gallery.data.duplicates

import android.content.Context
import android.net.Uri
import com.femi.gallery.data.db.HashDao
import com.femi.gallery.data.db.HashRow
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.duplicates.FileHasher
import com.femi.gallery.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SHA-256 fingerprints of files read through their content address. They are kept in the index
 * database, so searching again, even after the app was closed, does not read every file again. A
 * fingerprint is only used while the file still has the size and date added it had when it was made,
 * so an edited file is read again.
 */
@Singleton
class ContentFileHasher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    private val dao: HashDao,
) : FileHasher {

    override suspend fun partialHash(item: MediaItem): String? = hash(item, partial = true)

    override suspend fun fullHash(item: MediaItem): String? = hash(item, partial = false)

    override suspend fun forgetExcept(ids: Set<Long>) = withContext(io) {
        dao.deleteAll(dao.allIds().filter { it !in ids })
    }

    private suspend fun hash(item: MediaItem, partial: Boolean): String? = withContext(io) {
        // What is remembered counts only if the file is still the one it was made from.
        val remembered = dao.get(item.id)?.takeIf { it.size == item.sizeBytes && it.dateAdded == item.dateAddedMillis }
        (if (partial) remembered?.partialHash else remembered?.fullHash)?.let { return@withContext it }

        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val opened = context.contentResolver.openFileDescriptor(Uri.parse(item.uri), "r") ?: return@withContext null
            opened.use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { stream ->
                    if (partial) digestPieces(stream.channel, digest) else digestEverything(stream, digest)
                }
            }
        } catch (e: IOException) {
            return@withContext null // gone, or not readable
        } catch (e: SecurityException) {
            return@withContext null
        }
        val result = digest.digest().joinToString("") { "%02x".format(it) }
        dao.upsert(
            HashRow(
                id = item.id,
                size = item.sizeBytes,
                dateAdded = item.dateAddedMillis,
                partialHash = if (partial) result else remembered?.partialHash,
                fullHash = if (partial) remembered?.fullHash else result,
            ),
        )
        result
    }

    /** The size and three pieces of the file: its start, its middle and its end. */
    private suspend fun digestPieces(channel: FileChannel, digest: MessageDigest) {
        val size = channel.size()
        digest.update(size.toString().toByteArray())
        val buffer = ByteBuffer.allocate(PIECE_BYTES)
        val starts = if (size <= PIECE_BYTES * 3L) {
            listOf(0L)
        } else {
            listOf(0L, size / 2 - PIECE_BYTES / 2, size - PIECE_BYTES)
        }
        for (start in starts) {
            currentCoroutineContext().ensureActive()
            buffer.clear()
            var position = start
            val end = if (starts.size == 1) size else start + PIECE_BYTES
            while (position < end && buffer.hasRemaining()) {
                val read = channel.read(buffer, position)
                if (read <= 0) break
                position += read
            }
            digest.update(buffer.array(), 0, buffer.position())
        }
    }

    private suspend fun digestEverything(stream: FileInputStream, digest: MessageDigest) {
        val buffer = ByteArray(READ_BYTES)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = stream.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }

    private companion object {
        const val PIECE_BYTES = 64 * 1024
        const val READ_BYTES = 256 * 1024
    }
}
