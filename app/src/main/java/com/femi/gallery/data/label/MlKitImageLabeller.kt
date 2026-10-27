package com.femi.gallery.data.label

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.provider.MediaStore
import android.util.Log
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.label.LabelMatcher
import com.femi.gallery.domain.repository.ImageLabeller
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * Names the objects and scenes in photos with ML Kit's bundled image labeller (about 400 everyday
 * categories such as "Dog", "Beach", "Food", "Car"). The model is part of the app and works offline.
 */
@Singleton
class MlKitImageLabeller @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ImageLabeller {

    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(MIN_CONFIDENCE).build())
    }

    /** Stored labels, strongest first; empty when the photo has nothing recognisable or cannot be read. */
    override suspend fun label(mediaId: Long): List<String> {
        val bitmap = decode(mediaId) ?: return emptyList()
        return try {
            labeler.process(InputImage.fromBitmap(bitmap, 0)).await()
                .sortedByDescending { it.confidence }
                .mapNotNull { LabelMatcher.normalize(it.text) }
                .distinct()
                .take(MAX_LABELS)
        } catch (e: Exception) {
            Log.w(TAG, "Could not label photo $mediaId", e)
            emptyList()
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun decode(mediaId: Long): Bitmap? = withContext(io) {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
        try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // ML Kit cannot read hardware bitmaps
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        } catch (e: IOException) {
            null // gone, damaged, or a format the decoder does not know
        }
    }

    private companion object {
        const val TAG = "LabelScan"
        const val MAX_SIDE = 480 // the model looks at about 224 px; more only costs time
        const val MIN_CONFIDENCE = 0.6f
        const val MAX_LABELS = 10
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
