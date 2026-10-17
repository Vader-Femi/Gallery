package com.femi.gallery.data.edit

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.edit.CropRect
import com.femi.gallery.domain.edit.EditState
import com.femi.gallery.domain.model.AlbumPaths
import com.femi.gallery.domain.repository.EditRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
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
        if (state.rotationTurns == 0 && !state.flipHorizontal && !state.flipVertical) return bitmap
        val matrix = Matrix().apply {
            postRotate(state.rotationTurns * 90f)
            if (state.flipHorizontal) postScale(-1f, 1f)
            if (state.flipVertical) postScale(1f, -1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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
        val result = Bitmap.createBitmap(geometry.width, geometry.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(state.colorMatrix()))
        }
        Canvas(result).drawBitmap(geometry, 0f, 0f, paint)
        geometry.recycle()

        val isPng = meta.mime == "image/png"
        val baseName = meta.name.substringBeforeLast('.', meta.name).ifEmpty { "photo" }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$baseName-edited.${if (isPng) "png" else "jpg"}")
            put(MediaStore.MediaColumns.MIME_TYPE, if (isPng) "image/png" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, destinationFor(meta.path))
            put(MediaStore.MediaColumns.DATE_TAKEN, meta.taken.takeIf { it > 0 } ?: (meta.addedSeconds * 1000L))
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
            true
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            false
        }
    }

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
        /** Longest side of a saved edit; a 50 MP photo would need ~200 MB of memory at full size. */
        const val MAX_SAVE_SIZE = 4096
        const val JPEG_QUALITY = 95
    }
}
