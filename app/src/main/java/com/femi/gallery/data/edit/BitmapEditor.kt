package com.femi.gallery.data.edit

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.femi.gallery.core.graphics.MarkupRenderer
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.edit.CropRect
import com.femi.gallery.domain.edit.EditState
import com.femi.gallery.domain.edit.Sharpen
import com.femi.gallery.domain.edit.StraightenMath
import com.femi.gallery.domain.edit.Vignette
import com.femi.gallery.domain.model.AlbumPaths
import com.femi.gallery.domain.repository.EditRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Singleton
import kotlin.math.hypot
import kotlin.math.roundToInt

@Singleton
class BitmapEditor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : EditRepository {

    override suspend fun loadPreview(uri: String, maxSize: Int): Bitmap? = withContext(io) {
        runCatching { decode(Uri.parse(uri), maxSize) }.getOrNull()
    }

    override suspend fun applyGeometry(bitmap: Bitmap, state: EditState, includeCrop: Boolean): Bitmap =
        withContext(io) {
            val oriented = orient(bitmap, state)
            if (includeCrop) crop(oriented, state.crop) else oriented
        }

    override suspend fun saveCopy(uri: String, state: EditState): Boolean = withContext(io) {
        runCatching { save(Uri.parse(uri), state) }.getOrDefault(false)
    }

    override suspend fun replaceOriginal(uri: String, state: EditState): Boolean = withContext(io) {
        runCatching { overwrite(Uri.parse(uri), state) }.getOrDefault(false)
    }

    override suspend fun canRevert(uri: String): Boolean = withContext(io) {
        val file = backupFileFor(Uri.parse(uri))
        file != null && file.exists() && file.length() > 0
    }

    override suspend fun revertOriginal(uri: String): Boolean = withContext(io) {
        runCatching { restoreBackup(Uri.parse(uri)) }.getOrDefault(false)
    }

    /** Decodes with the photo's orientation applied, scaled so the longest side is at most [maxSize]. */
    private fun decode(uri: Uri, maxSize: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Software bitmaps can be drawn into and transformed; hardware ones cannot.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > maxSize) {
                val scale = maxSize.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
    }

    /** Rotation first, then flips, so "flip horizontal" always means left-right on screen. */
    private fun orient(bitmap: Bitmap, state: EditState): Bitmap {
        if (state.rotationTurns == 0 && state.straightenAngle == 0f && !state.flipHorizontal && !state.flipVertical) return bitmap
        val matrix = Matrix().apply {
            postRotate(state.rotationTurns * 90f + state.straightenAngle)
            if (state.flipHorizontal) postScale(-1f, 1f)
            if (state.flipVertical) postScale(1f, -1f)
        }
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return if (state.straightenAngle == 0f) turned else trimCorners(turned, bitmap, state)
    }

    /**
     * A bitmap turned by a few degrees is surrounded by empty corners; this keeps the biggest centred
     * rectangle of the picture's own shape that has none (see [StraightenMath]).
     */
    private fun trimCorners(turned: Bitmap, original: Bitmap, state: EditState): Bitmap {
        val sideways = state.rotationTurns % 2 != 0
        val width = if (sideways) original.height else original.width
        val height = if (sideways) original.width else original.height
        val (left, top, w, h) = StraightenMath.trim(width, height, turned.width, turned.height, state.straightenAngle)
        val trimmed = Bitmap.createBitmap(turned, left, top, w, h)
        if (trimmed !== turned) turned.recycle()
        return trimmed
    }

    /** Sharpen and vignette. They change pixels, so they come after the crop and before the colour matrix. */
    override suspend fun applyEffects(bitmap: Bitmap, state: EditState): Bitmap = withContext(io) {
        renderEffects(bitmap, state)
    }

    private fun renderEffects(source: Bitmap, state: EditState): Bitmap {
        if (state.sharpen <= 0f && state.vignette <= 0f) return source
        var result = source
        if (state.sharpen > 0f) result = sharpen(result, Sharpen.amountFor(state.sharpen))
        if (state.vignette > 0f) {
            val target = if (result === source) source.copy(Bitmap.Config.ARGB_8888, true) else result
            drawVignette(Canvas(target), target.width, target.height, state.vignette)
            result = target
        }
        return result
    }

    /** Row by row, so no second copy of all the pixels is needed (see [Sharpen]). */
    private fun sharpen(source: Bitmap, amount: Float): Bitmap {
        val w = source.width
        val h = source.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        var above = IntArray(w)
        var current = IntArray(w)
        var below = IntArray(w)
        val result = IntArray(w)
        source.getPixels(current, 0, w, 0, 0, w, 1)
        System.arraycopy(current, 0, above, 0, w)
        for (y in 0 until h) {
            if (y < h - 1) source.getPixels(below, 0, w, 0, y + 1, w, 1) else System.arraycopy(current, 0, below, 0, w)
            Sharpen.row(above, current, below, result, amount)
            out.setPixels(result, 0, w, 0, y, w, 1)
            val spare = above
            above = current
            current = below
            below = spare
        }
        return out
    }

    private fun drawVignette(canvas: Canvas, width: Int, height: Int, value: Float) {
        val stops = Vignette.stops(value)
        val shader = RadialGradient(
            width / 2f,
            height / 2f,
            hypot(width / 2f, height / 2f),
            IntArray(stops.size) { AndroidColor.argb((stops[it].second * 255f).roundToInt(), 0, 0, 0) },
            FloatArray(stops.size) { stops[it].first },
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { this.shader = shader })
    }

    private fun crop(bitmap: Bitmap, rect: CropRect): Bitmap {
        if (rect.isFull) return bitmap
        val left = (rect.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (rect.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val width = (rect.width * bitmap.width).roundToInt().coerceIn(1, bitmap.width - left)
        val height = (rect.height * bitmap.height).roundToInt().coerceIn(1, bitmap.height - top)
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    private fun save(uri: Uri, state: EditState): Boolean {
        val resolver = context.contentResolver
        val row = ContentUris.withAppendedId(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), ContentUris.parseId(uri))
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val meta = resolver.query(row, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                Original(
                    name = cursor.getString(0).orEmpty(),
                    mime = cursor.getString(1).orEmpty(),
                    taken = cursor.getLong(2),
                    addedSeconds = cursor.getLong(3),
                    path = cursor.getString(4).orEmpty(),
                )
            } else {
                null
            }
        } ?: return false

        // Edits are rendered from the photo itself (up to MAX_SAVE_SIZE) rather than the preview.
        val full = decode(uri, MAX_SAVE_SIZE)
        val oriented = orient(full, state)
        if (oriented !== full) full.recycle() // a 4096 px bitmap is ~50 MB; free copies as soon as possible
        val geometry = crop(oriented, state.crop)
        if (geometry !== oriented) oriented.recycle()
        val effected = renderEffects(geometry, state)
        if (effected !== geometry) geometry.recycle()
        val result = Bitmap.createBitmap(effected.width, effected.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(state.colorMatrix()))
        }
        val canvas = Canvas(result)
        canvas.drawBitmap(effected, 0f, 0f, paint)
        effected.recycle()
        // Drawings, text and stickers go on last, so that the colours of the edit do not tint them.
        MarkupRenderer.draw(canvas, result.width.toFloat(), result.height.toFloat(), state.markup)

        val isPng = meta.mime == "image/png"
        val baseName = meta.name.substringBeforeLast('.', meta.name).ifEmpty { "photo" }
        val takenMillis = meta.taken.takeIf { it > 0 } ?: (meta.addedSeconds * 1000L)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName-edited.${if (isPng) "png" else "jpg"}")
            put(MediaStore.MediaColumns.MIME_TYPE, if (isPng) "image/png" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, destinationFor(meta.path))
            put(MediaStore.MediaColumns.DATE_TAKEN, takenMillis)
            put(MediaStore.MediaColumns.DATE_MODIFIED, takenMillis / 1000L)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: return false
        return try {
            val output = resolver.openOutputStream(target) ?: error("Cannot write $target")
            output.use {
                if (isPng) result.compress(Bitmap.CompressFormat.PNG, 100, it)
                else result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
            }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)

            // Copy EXIF from original using read-write mode
            runCatching {
                resolver.openFileDescriptor(uri, "r")?.use { sourceFd ->
                    resolver.openFileDescriptor(target, "rw")?.use { targetFd ->
                        val sourceExif = ExifInterface(sourceFd.fileDescriptor)
                        val targetExif = ExifInterface(targetFd.fileDescriptor)
                        val tags = EXIF_TAGS
                        for (tag in tags) {
                            sourceExif.getAttribute(tag)?.let { targetExif.setAttribute(tag, it) }
                        }
                        // The saved pixels are already upright (the decoder applied the original's
                        // orientation), so copying that tag would turn the picture a second time.
                        targetExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                        targetExif.saveAttributes()
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Could not copy EXIF attributes to edited copy", e)
            }

            true
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            false
        }
    }

    private fun overwrite(uri: Uri, state: EditState): Boolean {
        val resolver = context.contentResolver
        val row = ContentUris.withAppendedId(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), ContentUris.parseId(uri))
        val projection = arrayOf(
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
        )
        val meta = resolver.query(row, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                Pair(cursor.getString(0).orEmpty(), cursor.getLong(1))
            } else {
                null
            }
        } ?: return false

        // Replacing keeps the file's own format and size: a HEIC, AVIF or other file would otherwise get JPEG bytes
        // under its old name, and a photo larger than the editing size would silently lose its resolution.
        val format = when (meta.first) {
            "image/jpeg" -> Bitmap.CompressFormat.JPEG
            "image/png" -> Bitmap.CompressFormat.PNG
            "image/webp" -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            else -> null
        }
        if (format == null) {
            Log.w(TAG, "Not replacing $uri: ${meta.first} cannot be written back in its own format; use Save as copy")
            return false
        }
        val longest = longestSide(uri)
        if (longest > MAX_SAVE_SIZE) {
            Log.w(TAG, "Not replacing $uri: it is $longest px on its longest side, more than the $MAX_SAVE_SIZE px edits are saved at; use Save as copy")
            return false
        }

        val full = decode(uri, MAX_SAVE_SIZE)
        val oriented = orient(full, state)
        if (oriented !== full) full.recycle()
        val geometry = crop(oriented, state.crop)
        if (geometry !== oriented) oriented.recycle()
        val effected = renderEffects(geometry, state)
        if (effected !== geometry) geometry.recycle()
        val result = Bitmap.createBitmap(effected.width, effected.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(state.colorMatrix()))
        }
        val canvas = Canvas(result)
        canvas.drawBitmap(effected, 0f, 0f, paint)
        effected.recycle()
        // Drawings, text and stickers go on last, so that the colours of the edit do not tint them.
        MarkupRenderer.draw(canvas, result.width.toFloat(), result.height.toFloat(), state.markup)

        // Encode first, in memory: if that fails, the original has not been touched.
        val encoded = ByteArrayOutputStream().use { buffer ->
            val quality = if (format == Bitmap.CompressFormat.PNG) 100 else JPEG_QUALITY
            if (!result.compress(format, quality, buffer)) {
                Log.e(TAG, "Could not encode the edited photo for $uri")
                result.recycle()
                return false
            }
            buffer.toByteArray()
        }
        result.recycle()

        // Read EXIF before truncating
        val exifMap = mutableMapOf<String, String>()
        runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { sourceFd ->
                val sourceExif = ExifInterface(sourceFd.fileDescriptor)
                for (tag in EXIF_TAGS) {
                    sourceExif.getAttribute(tag)?.let { exifMap[tag] = it }
                }
            }
        }

        // Keep the original's bytes until the new ones are written; put them back if writing fails half way.
        val backup = File(context.cacheDir, "replace_backup.bin")
        val backedUp = runCatching {
            resolver.openInputStream(uri)?.use { input -> backup.outputStream().use { input.copyTo(it) } } != null
        }.getOrDefault(false)
        if (!backedUp) {
            Log.w(TAG, "Not replacing $uri: its original bytes could not be kept as a backup")
            backup.delete()
            return false
        }

        // Preserve the original untouched bytes in app-private storage (filesDir/revert_backups/{id}.bin)
        // If an untouched backup already exists from a previous edit, keep that one rather than overwriting
        // it with an intermediate edit.
        val revertFile = backupFileFor(uri)
        val hadRevertBackup = revertFile?.exists() == true && revertFile.length() > 0
        var createdRevertBackup = false
        if (!hadRevertBackup && revertFile != null) {
            val dir = revertFile.parentFile
            if (dir != null && !dir.exists()) dir.mkdirs()
            if (dir != null) pruneBackupsIfExceeded(dir, backup.length())
            createdRevertBackup = runCatching {
                backup.inputStream().use { input -> revertFile.outputStream().use { input.copyTo(it) } } > 0
            }.getOrDefault(false)
        }

        return try {
            val output = resolver.openOutputStream(uri, "wt") ?: error("Cannot write $uri")
            output.use { it.write(encoded) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000L) }, null, null)

            // Write EXIF back
            runCatching {
                resolver.openFileDescriptor(uri, "rw")?.use { targetFd ->
                    val targetExif = ExifInterface(targetFd.fileDescriptor)
                    for ((tag, value) in exifMap) {
                        targetExif.setAttribute(tag, value)
                    }
                    targetExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                    targetExif.saveAttributes()
                }
            }.onFailure { e ->
                Log.w(TAG, "Could not write EXIF attributes to overwritten original", e)
            }

            backup.delete()
            coil3.SingletonImageLoader.get(context).memoryCache?.clear()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to overwrite $uri; putting the original back", e)
            val restored = runCatching {
                resolver.openOutputStream(uri, "wt")?.use { out -> backup.inputStream().use { it.copyTo(out) } } != null
            }.getOrDefault(false)
            if (restored) backup.delete() else Log.e(TAG, "The original of $uri could not be restored; the backup stays in ${backup.path}")
            if (createdRevertBackup) revertFile?.delete()
            false
        }
    }

    private fun restoreBackup(uri: Uri): Boolean {
        val backup = backupFileFor(uri)
        if (backup == null || !backup.exists() || backup.length() == 0L) {
            Log.w(TAG, "No revert backup found for $uri")
            return false
        }
        val resolver = context.contentResolver
        return try {
            val output = resolver.openOutputStream(uri, "wt") ?: error("Cannot write $uri")
            output.use { out ->
                backup.inputStream().use { it.copyTo(out) }
            }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000L) },
                null,
                null,
            )
            backup.delete()
            coil3.SingletonImageLoader.get(context).memoryCache?.clear()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore original for $uri", e)
            false
        }
    }

    private fun backupDir(): File = File(context.filesDir, REVERT_BACKUPS_DIR).also {
        if (!it.exists()) it.mkdirs()
    }

    private fun backupFileFor(uri: Uri): File? {
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull()
        val name = if (id != null && id >= 0) "$id.bin" else "${uri.toString().hashCode()}.bin"
        return File(backupDir(), name)
    }

    private fun pruneBackupsIfExceeded(dir: File, neededBytes: Long) {
        val files = dir.listFiles()?.toList() ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize + neededBytes <= MAX_BACKUPS_BYTES) return
        val sortedByAge = files.sortedBy { it.lastModified() }
        for (f in sortedByAge) {
            if (totalSize + neededBytes <= MAX_BACKUPS_BYTES) break
            val size = f.length()
            if (f.delete()) {
                totalSize -= size
            }
        }
    }

    /** The longest side of the picture in pixels, read from its header only; 0 when it cannot be read. */
    private fun longestSide(uri: Uri): Int = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(0)
    }.getOrDefault(0)

    /** Next to the original when apps may write there, otherwise in Pictures/Edited. */
    private fun destinationFor(originalPath: String): String =
        if (AlbumPaths.isAlbumFolder(originalPath)) AlbumPaths.forMedia(originalPath, isVideo = false) else "Pictures/Edited/"

    private class Original(
        val name: String,
        val mime: String,
        val taken: Long,
        val addedSeconds: Long,
        val path: String,
    )

    private companion object {
        const val TAG = "BitmapEditor"
        const val REVERT_BACKUPS_DIR = "revert_backups"
        const val MAX_BACKUPS_BYTES = 500L * 1024L * 1024L // 500 MB
        /** Longest side of a saved edit; a 50 MP photo would need ~200 MB of memory at full size. */
        const val MAX_SAVE_SIZE = 4096
        const val JPEG_QUALITY = 95
        
        val EXIF_TAGS = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_EXPOSURE_PROGRAM,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_FLASH,
        )
    }
}
