package com.femi.gallery.data.locked

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.locked.ContentCipher
import com.femi.gallery.domain.locked.LockedAead
import com.femi.gallery.domain.locked.LockedItemHeader
import com.femi.gallery.domain.locked.LockedKeyKind
import com.femi.gallery.domain.locked.LockedMeta
import com.femi.gallery.domain.locked.OffsetChannel
import com.femi.gallery.domain.model.LockedCopy
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * The Locked folder: photos and videos stored in the app's private storage, where other apps (including other
 * galleries and the system MediaStore) cannot see them.
 *
 * Each item is one encrypted file, `files/locked/<id>.lk`: a header with the item's own key and its metadata (name,
 * dates, size, original folder, ...), both sealed with the folder key ([LockedKeys]), followed by the photo or video
 * encrypted in segments ([ContentCipher]). Nothing readable is left on the phone. The app shows the items through
 * [LockedProvider], which decrypts on the fly. Files here are not backed up by Android (see the backup rules); the
 * person can make an encrypted backup of their own ([LockedBackup]).
 *
 * Items made by older versions (a plain media file and a `<id>.json`) are encrypted in the background; until then they
 * are still listed and shown from their plain files.
 */
@Singleton
class LockedStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    private val keys: LockedKeys,
    private val cipher: ContentCipher,
) {
    val directory = File(context.filesDir, DIR_NAME)
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val migrationLock = Mutex()
    private val authority = LockedProvider.authority(context)

    private val _version = MutableStateFlow(0L)

    /** Increases whenever the folder's contents change. */
    val version: StateFlow<Long> = _version.asStateFlow()

    @Volatile
    private var cache: List<MediaItem>? = null

    init {
        CoroutineScope(SupervisorJob() + io).launch { migrateLegacy() }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Everything in the folder as media items, newest first. Empty (and not remembered) while the folder key cannot be
     * had, for instance before the person has authenticated.
     */
    suspend fun list(): List<MediaItem> = withContext(io) {
        cache ?: readAll()?.also { cache = it } ?: emptyList()
    }

    /** Dates (epoch seconds) in the same order as [list], for building day headers. */
    suspend fun datesSeconds(): LongArray {
        val items = list()
        return LongArray(items.size) { items[it].dateTakenMillis / 1000 }
    }

    /** Copies [uris] into the folder, encrypted. Items that fail to copy are skipped. */
    suspend fun copyIn(uris: List<String>): List<LockedCopy> = withContext(io) {
        directory.mkdirs()
        val folderKey = runCatching { keys.folderKey() }.getOrElse {
            Log.w(TAG, "Cannot copy into the Locked folder: no folder key", it)
            return@withContext emptyList()
        }
        val copies = uris.map { uri ->
            async {
                runCatching { copyOne(Uri.parse(uri), folderKey) }
                    .onFailure { Log.w(TAG, "Cannot lock $uri", it) }
                    .getOrNull()?.let { LockedCopy(it, uri) }
            }
        }.awaitAll().filterNotNull()

        if (copies.isNotEmpty()) changed()
        copies
    }

    /** Puts items back into the gallery (Pictures / Movies / DCIM); returns how many succeeded. */
    suspend fun restore(ids: List<Long>): Int = withContext(io) {
        val restored = ids.map { id ->
            async { runCatching { restoreOne(id) }.onFailure { Log.w(TAG, "Cannot unlock $id", it) }.getOrDefault(false) }
        }.awaitAll().count { it }

        if (restored > 0) changed()
        restored
    }

    suspend fun delete(ids: List<Long>) = withContext(io) {
        ids.map { id ->
            async { directory.listFiles { file -> file.name.startsWith("$id.") }?.forEach { it.delete() } }
        }.awaitAll()
        changed()
    }

    /** The header and metadata of the encrypted item [id]; null when there is no such item or it cannot be opened. */
    fun metaOrNull(id: Long): LockedMeta? = runCatching {
        val file = File(directory, "$id.lk")
        if (!file.exists()) return null
        readHeader(file).openMeta(keys.folderKey())
    }.getOrNull()

    /**
     * A channel on the decrypted contents of the encrypted item [id], which can be read from any position, with the
     * item's metadata; null when there is no such item. Throws when the item or the folder key cannot be opened.
     */
    fun openContents(id: Long): Pair<SeekableByteChannel, LockedMeta>? {
        val file = File(directory, "$id.lk")
        if (!file.exists()) return null
        val folderKey = keys.folderKey()
        val header = readHeader(file)
        val fileKey = header.openFileKey(folderKey)
        val meta = header.openMeta(folderKey)
        val channel = FileInputStream(file).channel
        try {
            return cipher.openSeekable(fileKey, OffsetChannel(channel, header.length.toLong())) to meta
        } catch (e: Exception) {
            channel.close()
            throw e
        }
    }

    /** Encrypts the items of older versions now (it also happens in the background); false if some could not be. */
    suspend fun migrateLegacy(): Boolean = migrationLock.withLock {
        withContext(io) {
            val legacy = directory.listFiles { file -> file.extension == "json" }.orEmpty()
                .filter { json -> !File(directory, "${json.nameWithoutExtension}.lk").exists() }
            if (legacy.isEmpty()) return@withContext true
            val folderKey = runCatching { keys.folderKey() }.getOrElse {
                Log.w(TAG, "Cannot encrypt the old items yet: no folder key", it)
                return@withContext false
            }
            Log.i(TAG, "Encrypting ${legacy.size} items of an older version")
            var failed = 0
            for (json in legacy) {
                runCatching { encryptLegacy(json, folderKey) }.onFailure {
                    failed++
                    Log.w(TAG, "Cannot encrypt ${json.name}; it stays as it was", it)
                }
            }
            changed()
            failed == 0
        }
    }

    /** Tells everything that shows the folder that its contents changed (after an import). */
    fun notifyChanged() = changed()

    private fun changed() {
        cache = null
        _version.update { it + 1 }
        listeners.forEach { it() }
    }

    private fun readHeader(file: File): LockedItemHeader =
        FileInputStream(file).use { LockedItemHeader.read(BufferedInputStream(it, HEADER_BUFFER)) }

    private fun copyOne(uri: Uri, folderKey: ByteArray): Long {
        val resolver = context.contentResolver
        // The generic files table has every column (the images table lacks DURATION, for one).
        val fileRow = ContentUris.withAppendedId(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            ContentUris.parseId(uri),
        )
        val id = Random.nextLong() and Long.MAX_VALUE
        val meta = resolver.query(fileRow, META_PROJECTION, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) readMeta(id, cursor) else null
        } ?: error("Item not found: $uri")

        val temporary = File(directory, "$id.lk.tmp")
        try {
            val source = resolver.openInputStream(uri) ?: error("Cannot open $uri")
            source.use { writeEncrypted(temporary, folderKey, meta, it) }
            if (!temporary.renameTo(File(directory, "$id.lk"))) error("Cannot move $temporary into place")
        } catch (e: Exception) {
            temporary.delete()
            throw e
        }
        return id
    }

    /** Writes header and encrypted contents of [plain] to [target], on disk before returning. */
    private fun writeEncrypted(target: File, folderKey: ByteArray, meta: LockedMeta, plain: java.io.InputStream) {
        val fileKey = LockedAead.newKey()
        val header = LockedItemHeader.create(LockedKeyKind.Folder, folderKey, fileKey, meta)
        FileOutputStream(target).use { file ->
            header.write(file)
            // Closing the encrypting stream writes its last segment; the file stays open for the sync below.
            val encrypting = cipher.encrypt(fileKey, NonClosingOutputStream(file))
            plain.copyTo(encrypting, COPY_BUFFER)
            encrypting.close()
            file.flush()
            file.fd.sync()
        }
    }

    private fun restoreOne(id: Long): Boolean {
        val encrypted = File(directory, "$id.lk")
        if (encrypted.exists()) return restoreEncrypted(encrypted)
        return restoreLegacy(id)
    }

    private fun restoreEncrypted(file: File): Boolean {
        val folderKey = keys.folderKey()
        val header = readHeader(file)
        val fileKey = header.openFileKey(folderKey)
        val meta = header.openMeta(folderKey)
        val isVideo = meta.mimeType.startsWith("video/")
        val resolver = context.contentResolver
        val target = resolver.insert(collectionFor(isVideo), valuesFor(meta.name, meta.mimeType, meta.relativePath, meta.dateTakenMillis, isVideo))
            ?: return false
        try {
            val out = resolver.openOutputStream(target) ?: error("Cannot write $target")
            out.use { sink ->
                FileInputStream(file).use { input ->
                    input.skipFully(header.length.toLong())
                    cipher.decrypt(fileKey, input).use { it.copyTo(sink, COPY_BUFFER) }
                }
            }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            throw e
        }
        file.delete()
        return true
    }

    private fun restoreLegacy(id: Long): Boolean {
        val jsonFile = File(directory, "$id.json")
        if (!jsonFile.exists()) return false
        val json = JSONObject(jsonFile.readText())
        val dataFile = File(directory, json.optString("file", "$id.bin"))
        if (!dataFile.exists()) return false
        val mime = json.optString("mime", "application/octet-stream")
        val isVideo = mime.startsWith("video/")
        val resolver = context.contentResolver
        val target = resolver.insert(
            collectionFor(isVideo),
            valuesFor(json.optString("name", "item"), mime, json.optString("relativePath"), json.optLong("dateTaken"), isVideo),
        ) ?: return false
        try {
            val out = resolver.openOutputStream(target) ?: error("Cannot write $target")
            out.use { sink -> dataFile.inputStream().use { it.copyTo(sink) } }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            return false
        }
        dataFile.delete()
        jsonFile.delete()
        return true
    }

    private fun collectionFor(isVideo: Boolean): Uri =
        if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    private fun valuesFor(name: String, mime: String, relativePath: String, dateTaken: Long, isVideo: Boolean) = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, restorePath(relativePath, isVideo))
        put(MediaStore.MediaColumns.DATE_TAKEN, dateTaken)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    /** Back to the original folder when apps may write there, otherwise to a "Restored" folder. */
    private fun restorePath(original: String, isVideo: Boolean): String {
        val allowed = if (isVideo) listOf("DCIM/", "Movies/") else listOf("DCIM/", "Pictures/")
        return if (allowed.any { original.startsWith(it) }) original else if (isVideo) "Movies/Restored/" else "Pictures/Restored/"
    }

    /**
     * Encrypts one item of an older version: the plain file is only deleted after the encrypted copy has been read
     * back and found to hold exactly the same bytes (compared by SHA-256), so a mistake here cannot lose a photo.
     */
    private fun encryptLegacy(jsonFile: File, folderKey: ByteArray) {
        val json = JSONObject(jsonFile.readText())
        val id = json.getLong("id")
        val dataFile = File(directory, json.getString("file"))
        if (!dataFile.exists()) return
        val meta = LockedMeta(
            id = id,
            name = json.optString("name", "item"),
            mimeType = json.optString("mime", "application/octet-stream"),
            dateTakenMillis = json.optLong("dateTaken"),
            dateAddedMillis = json.optLong("dateAdded"),
            width = json.optInt("width"),
            height = json.optInt("height"),
            sizeBytes = dataFile.length(),
            durationMillis = json.optLong("duration"),
            relativePath = json.optString("relativePath"),
        )
        val temporary = File(directory, "$id.lk.tmp")
        try {
            val before = MessageDigest.getInstance("SHA-256")
            FileInputStream(dataFile).use { plain ->
                writeEncrypted(temporary, folderKey, meta, java.security.DigestInputStream(plain, before))
            }

            val after = MessageDigest.getInstance("SHA-256")
            val header = readHeader(temporary)
            val fileKey = header.openFileKey(folderKey)
            var total = 0L
            FileInputStream(temporary).use { input ->
                input.skipFully(header.length.toLong())
                cipher.decrypt(fileKey, input).use { plain ->
                    val buffer = ByteArray(COPY_BUFFER)
                    while (true) {
                        val n = plain.read(buffer)
                        if (n < 0) break
                        after.update(buffer, 0, n)
                        total += n
                    }
                }
            }
            check(total == dataFile.length() && MessageDigest.isEqual(before.digest(), after.digest())) {
                "The encrypted copy of ${dataFile.name} does not match the original"
            }
            if (!temporary.renameTo(File(directory, "$id.lk"))) error("Cannot move $temporary into place")
        } catch (e: Exception) {
            temporary.delete()
            throw e
        }
        dataFile.delete()
        jsonFile.delete()
    }

    private fun readAll(): List<MediaItem>? {
        val folderKey = try {
            keys.folderKey()
        } catch (e: LockedKeyException) {
            Log.w(TAG, "Cannot list the Locked folder: ${e.message}")
            return null
        }
        val items = ArrayList<MediaItem>()
        for (file in directory.listFiles { f -> f.extension == "lk" }.orEmpty()) {
            runCatching { readHeader(file).openMeta(folderKey) }
                .onSuccess { items.add(it.toItem()) }
                .onFailure { Log.w(TAG, "Cannot read ${file.name}", it) }
        }
        // Items of an older version that are not encrypted yet.
        for (json in directory.listFiles { f -> f.extension == "json" }.orEmpty()) {
            if (File(directory, "${json.nameWithoutExtension}.lk").exists()) continue
            runCatching { legacyItem(JSONObject(json.readText())) }.getOrNull()?.let(items::add)
        }
        return items.sortedWith(compareByDescending<MediaItem> { it.dateTakenMillis }.thenByDescending { it.id })
    }

    private fun LockedMeta.toItem(): MediaItem {
        val isVideo = mimeType.startsWith("video/")
        return MediaItem(
            id = id,
            uri = LockedProvider.uriFor(authority, id).toString(),
            displayName = name,
            mimeType = mimeType,
            type = if (isVideo) MediaType.Video else MediaType.Image,
            dateTakenMillis = dateTakenMillis,
            dateAddedMillis = dateAddedMillis,
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            durationMillis = durationMillis,
            bucketId = 0L,
            bucketName = "",
            relativePath = relativePath,
            isFavorite = false,
        )
    }

    private fun legacyItem(json: JSONObject): MediaItem? {
        val file = File(directory, json.getString("file"))
        if (!file.exists()) return null
        val mime = json.optString("mime", "application/octet-stream")
        val isVideo = mime.startsWith("video/")
        return MediaItem(
            id = json.getLong("id"),
            uri = Uri.fromFile(file).toString(),
            displayName = json.optString("name", "item"),
            mimeType = mime,
            type = if (isVideo) MediaType.Video else MediaType.Image,
            dateTakenMillis = json.optLong("dateTaken"),
            dateAddedMillis = json.optLong("dateAdded"),
            width = json.optInt("width"),
            height = json.optInt("height"),
            sizeBytes = json.optLong("size"),
            durationMillis = json.optLong("duration"),
            bucketId = 0L,
            bucketName = "",
            relativePath = json.optString("relativePath"),
            isFavorite = false,
        )
    }

    private fun readMeta(id: Long, cursor: Cursor): LockedMeta {
        fun long(column: String) = cursor.getColumnIndex(column).takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0L
        fun string(column: String) = cursor.getColumnIndex(column).takeIf { it >= 0 }?.let { cursor.getString(it) }.orEmpty()
        val added = long(MediaStore.MediaColumns.DATE_ADDED) * 1000L
        return LockedMeta(
            id = id,
            name = string(MediaStore.MediaColumns.DISPLAY_NAME).ifEmpty { "item" },
            mimeType = string(MediaStore.MediaColumns.MIME_TYPE).ifEmpty { "application/octet-stream" },
            dateTakenMillis = long(MediaStore.MediaColumns.DATE_TAKEN).takeIf { it > 0 } ?: added,
            dateAddedMillis = added,
            width = long(MediaStore.MediaColumns.WIDTH).toInt(),
            height = long(MediaStore.MediaColumns.HEIGHT).toInt(),
            sizeBytes = long(MediaStore.MediaColumns.SIZE),
            durationMillis = long(MediaStore.MediaColumns.DURATION),
            relativePath = string(MediaStore.MediaColumns.RELATIVE_PATH),
        )
    }

    @Suppress("unused")
    private fun extensionFor(name: String, mime: String): String {
        val fromName = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
        return fromName ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
    }

    /** Lets a stream that closes its output (the encrypting stream) leave the file open for a final sync. */
    private class NonClosingOutputStream(private val out: OutputStream) : OutputStream() {
        override fun write(b: Int) = out.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun flush() = out.flush()
        override fun close() = out.flush()
    }

    private fun FileInputStream.skipFully(count: Long) {
        var left = count
        while (left > 0) {
            val skipped = skip(left)
            if (skipped <= 0) throw java.io.EOFException("Cannot skip the header")
            left -= skipped
        }
    }

    companion object {
        private const val TAG = "LockedStore"
        const val DIR_NAME = "locked"
        private const val COPY_BUFFER = 256 * 1024
        private const val HEADER_BUFFER = 1024

        private val META_PROJECTION = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
    }
}
