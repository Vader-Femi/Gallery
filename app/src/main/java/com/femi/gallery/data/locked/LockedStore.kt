package com.femi.gallery.data.locked

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.model.LockedCopy
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * The Locked folder: photos and videos stored in the app's private storage, where other apps
 * (including other galleries and the system MediaStore) cannot see them.
 *
 * Each item is two files in `files/locked/`: the media file itself (`<id>.<ext>`) and a small
 * `<id>.json` with what is needed to show it and to put it back (name, dates, size, original
 * folder, ...). Files here are not backed up (see the backup rules) and are lost if the app's
 * data is cleared or the app is uninstalled.
 */
@Singleton
class LockedStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dir = File(context.filesDir, DIR_NAME)
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    private val _version = MutableStateFlow(0L)

    /** Increases whenever the folder's contents change. */
    val version: StateFlow<Long> = _version.asStateFlow()

    @Volatile
    private var cache: List<MediaItem>? = null

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /** Everything in the folder as media items, newest first. */
    suspend fun list(): List<MediaItem> = withContext(io) {
        cache ?: readAll().also { cache = it }
    }

    /** Dates (epoch seconds) in the same order as [list], for building day headers. */
    suspend fun datesSeconds(): LongArray {
        val items = list()
        return LongArray(items.size) { items[it].dateAddedMillis / 1000 }
    }

    /** Copies [uris] into the folder. Items that fail to copy are skipped. */
    suspend fun copyIn(uris: List<String>): List<LockedCopy> = withContext(io) {
        dir.mkdirs()
        val copies = uris.mapNotNull { uri ->
            runCatching { copyOne(Uri.parse(uri)) }.getOrNull()?.let { LockedCopy(it, uri) }
        }
        if (copies.isNotEmpty()) changed()
        copies
    }

    /** Puts items back into the gallery (Pictures / Movies / DCIM); returns how many succeeded. */
    suspend fun restore(ids: List<Long>): Int = withContext(io) {
        val restored = ids.count { id -> runCatching { restoreOne(id) }.getOrDefault(false) }
        if (restored > 0) changed()
        restored
    }

    suspend fun delete(ids: List<Long>) = withContext(io) {
        ids.forEach { id ->
            dir.listFiles { file -> file.name.startsWith("$id.") }?.forEach { it.delete() }
        }
        changed()
    }

    private fun changed() {
        cache = null
        _version.value = _version.value + 1
        listeners.forEach { it() }
    }

    private fun copyOne(uri: Uri): Long {
        val resolver = context.contentResolver
        // The generic files table has every column (the images table lacks DURATION, for one).
        val fileRow = ContentUris.withAppendedId(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            ContentUris.parseId(uri),
        )
        val meta = resolver.query(fileRow, META_PROJECTION, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) readMeta(cursor) else null
        } ?: error("Item not found: $uri")

        val id = Random.nextLong() and Long.MAX_VALUE
        val fileName = "$id.${extensionFor(meta.name, meta.mime)}"
        val dataFile = File(dir, fileName)
        val jsonFile = File(dir, "$id.json")
        try {
            val input = resolver.openInputStream(uri) ?: error("Cannot open $uri")
            input.use { source -> FileOutputStream(dataFile).use { out -> source.copyTo(out) } }
            jsonFile.writeText(meta.toJson(id, fileName).toString())
        } catch (e: Exception) {
            dataFile.delete()
            jsonFile.delete()
            throw e
        }
        return id
    }

    private fun restoreOne(id: Long): Boolean {
        val json = JSONObject(File(dir, "$id.json").readText())
        val dataFile = File(dir, json.getString("file"))
        val mime = json.getString("mime")
        val isVideo = mime.startsWith("video/")
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, json.getString("name"))
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, restorePath(json.optString("relativePath"), isVideo))
            put(MediaStore.MediaColumns.DATE_TAKEN, json.optLong("dateTaken"))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val target = resolver.insert(collection, values) ?: return false
        try {
            val out = resolver.openOutputStream(target) ?: error("Cannot write $target")
            out.use { sink -> dataFile.inputStream().use { it.copyTo(sink) } }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            return false
        }
        dataFile.delete()
        File(dir, "$id.json").delete()
        return true
    }

    /** Back to the original folder when apps may write there, otherwise to a "Restored" folder. */
    private fun restorePath(original: String, isVideo: Boolean): String {
        val allowed = if (isVideo) listOf("DCIM/", "Movies/") else listOf("DCIM/", "Pictures/")
        return if (allowed.any { original.startsWith(it) }) original else if (isVideo) "Movies/Restored/" else "Pictures/Restored/"
    }

    private fun readAll(): List<MediaItem> =
        dir.listFiles { file -> file.extension == "json" }.orEmpty()
            .mapNotNull { file -> runCatching { toItem(JSONObject(file.readText())) }.getOrNull() }
            .sortedWith(compareByDescending<MediaItem> { it.dateAddedMillis }.thenByDescending { it.id })

    private fun toItem(json: JSONObject): MediaItem? {
        val file = File(dir, json.getString("file"))
        if (!file.exists()) return null
        val mime = json.getString("mime")
        val isVideo = mime.startsWith("video/")
        return MediaItem(
            id = json.getLong("id"),
            uri = Uri.fromFile(file).toString(),
            displayName = json.getString("name"),
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

    private class Meta(
        val name: String,
        val mime: String,
        val dateTaken: Long,
        val dateAdded: Long,
        val width: Int,
        val height: Int,
        val size: Long,
        val duration: Long,
        val relativePath: String,
    ) {
        fun toJson(id: Long, file: String): JSONObject = JSONObject()
            .put("id", id)
            .put("file", file)
            .put("name", name)
            .put("mime", mime)
            .put("dateTaken", dateTaken)
            .put("dateAdded", dateAdded)
            .put("width", width)
            .put("height", height)
            .put("size", size)
            .put("duration", duration)
            .put("relativePath", relativePath)
    }

    private fun readMeta(cursor: Cursor): Meta {
        fun long(column: String) = cursor.getColumnIndex(column).takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0L
        fun string(column: String) = cursor.getColumnIndex(column).takeIf { it >= 0 }?.let { cursor.getString(it) }.orEmpty()
        val added = long(MediaStore.MediaColumns.DATE_ADDED) * 1000L
        return Meta(
            name = string(MediaStore.MediaColumns.DISPLAY_NAME).ifEmpty { "item" },
            mime = string(MediaStore.MediaColumns.MIME_TYPE).ifEmpty { "application/octet-stream" },
            dateTaken = long(MediaStore.MediaColumns.DATE_TAKEN).takeIf { it > 0 } ?: added,
            dateAdded = added,
            width = long(MediaStore.MediaColumns.WIDTH).toInt(),
            height = long(MediaStore.MediaColumns.HEIGHT).toInt(),
            size = long(MediaStore.MediaColumns.SIZE),
            duration = long(MediaStore.MediaColumns.DURATION),
            relativePath = string(MediaStore.MediaColumns.RELATIVE_PATH),
        )
    }

    private fun extensionFor(name: String, mime: String): String {
        val fromName = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
        return fromName ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
    }

    private companion object {
        const val DIR_NAME = "locked"

        val META_PROJECTION = arrayOf(
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
