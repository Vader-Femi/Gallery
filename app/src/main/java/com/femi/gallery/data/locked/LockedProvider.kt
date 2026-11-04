package com.femi.gallery.data.locked

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hands the decrypted contents of locked items to the rest of the app as `content://<package>.locked/<id>`, so that the
 * image loader, the zoomable viewer, the video player and the metadata readers keep working with a plain URI and no
 * decrypted file is ever written to disk. The provider is not exported: no other app can reach it.
 *
 * `openFile` returns a file descriptor whose reads are answered by [LockedFileCallback] (Android's proxy file
 * descriptors), so that the video player can seek in an encrypted video.
 */
class LockedProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Entry {
        fun lockedStore(): LockedStore
    }

    private val store: LockedStore
        get() = EntryPointAccessors.fromApplication(requireNotNull(context).applicationContext, Entry::class.java).lockedStore()

    override fun onCreate(): Boolean = true

    private fun idOf(uri: Uri): Long = uri.lastPathSegment?.toLongOrNull() ?: throw FileNotFoundException("Not a locked item: $uri")

    override fun getType(uri: Uri): String? = store.metaOrNull(idOf(uri))?.mimeType

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val meta = store.metaOrNull(idOf(uri)) ?: return null
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> meta.name
                OpenableColumns.SIZE -> meta.sizeBytes
                else -> null
            }
        }
        return MatrixCursor(columns).apply { addRow(row) }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Locked items are read-only")
        val id = idOf(uri)
        val opened = try {
            store.openContents(id)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open locked item $id", e)
            throw FileNotFoundException("Cannot open locked item $id: ${e.message}")
        } ?: throw FileNotFoundException("No locked item $id")
        val storage = requireNotNull(context).getSystemService(StorageManager::class.java)
        return try {
            storage.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, LockedFileCallback(opened.first), nextHandler())
        } catch (e: Exception) {
            runCatching { opened.first.close() }
            throw FileNotFoundException("Cannot serve locked item $id: ${e.message}")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read-only")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only")

    /** Answers the reads of one open descriptor from the decrypting channel. */
    private class LockedFileCallback(private val channel: SeekableByteChannel) : ProxyFileDescriptorCallback() {
        override fun onGetSize(): Long = try {
            channel.size()
        } catch (e: Exception) {
            Log.w(TAG, "Cannot get the size", e)
            throw ErrnoException("onGetSize", OsConstants.EIO)
        }

        override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
            try {
                channel.position(offset)
                val buffer = ByteBuffer.wrap(data, 0, size)
                var total = 0
                while (buffer.hasRemaining()) {
                    val n = channel.read(buffer)
                    if (n < 0) break
                    total += n
                }
                return total
            } catch (e: Exception) {
                Log.w(TAG, "Cannot read at $offset", e)
                throw ErrnoException("onRead", OsConstants.EIO)
            }
        }

        override fun onRelease() {
            runCatching { channel.close() }
        }
    }

    companion object {
        private const val TAG = "LockedProvider"
        private const val THREADS = 4
        private val next = AtomicInteger()

        // Several threads, so that the thumbnails of a grid are not decrypted one after another.
        private val handlers: List<Handler> by lazy {
            List(THREADS) { Handler(HandlerThread("locked-files-$it").apply { start() }.looper) }
        }

        private fun nextHandler(): Handler = handlers[Math.floorMod(next.getAndIncrement(), THREADS)]

        fun authority(context: Context): String = context.packageName + ".locked"

        fun uriFor(authority: String, id: Long): Uri = Uri.parse("content://$authority/$id")
    }
}
