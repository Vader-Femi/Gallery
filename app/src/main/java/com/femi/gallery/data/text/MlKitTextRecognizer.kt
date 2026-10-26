package com.femi.gallery.data.text

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.provider.MediaStore
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.repository.TextRecognizer
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * Reads the text in photos with ML Kit's bundled Latin-script model. Everything happens on the phone:
 * the model is part of the app, and the app has no internet permission (the one ML Kit adds to its
 * own manifest is removed in AndroidManifest.xml).
 */
@Singleton
class MlKitTextRecognizer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : TextRecognizer {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(mediaId: Long): String? {
        val bitmap = try {
            decode(mediaId)
        } catch (e: UnreadableImageException) {
            return "" // exists, but is not an image we can open: nothing to read, and no point trying again
        } ?: return null // gone: tried again later
        return try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text.take(MAX_CHARACTERS)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * The photo as a bitmap, upright (the decoder applies the rotation) and at most [MAX_SIDE] pixels
     * on its longer side: big enough to read the small print of a screenshot, small enough to be fast.
     * Null when the photo is gone.
     *
     * @throws UnreadableImageException when the file exists but cannot be decoded
     */
    private suspend fun decode(mediaId: Long): Bitmap? = withContext(io) {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
        try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                // ML Kit cannot read hardware bitmaps.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IOException) {
            // Damaged, or a format the decoder does not know.
            throw UnreadableImageException(e)
        }
    }

    private companion object {
        const val MAX_SIDE = 2048
        const val MAX_CHARACTERS = 4_000
    }
}

/** The photo exists but is not an image the decoder can open. */
class UnreadableImageException(cause: Throwable) : Exception(cause)

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
