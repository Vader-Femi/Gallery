package com.femi.gallery.data.edit

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
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
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
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
        Canvas(result).drawBitmap(effected, 0f, 0f, paint)
        effected.recycle()

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
                        val tags = listOf(
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
        /** Longest side of a saved edit; a 50 MP photo would need ~200 MB of memory at full size. */
        const val MAX_SAVE_SIZE = 4096
        const val JPEG_QUALITY = 95
    }
}
