package com.femi.gallery.data.face

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.RectF
import android.provider.MediaStore
import android.util.Log
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.face.DetectedFace
import com.femi.gallery.domain.face.FaceFeatureHelper
import com.femi.gallery.domain.repository.FaceDetector
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
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

import com.femi.gallery.domain.repository.ImageEmbedder

@Singleton
class MlKitFaceDetector @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    private val embedder: ImageEmbedder,
) : FaceDetector {

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.12f)
            .build()
        FaceDetection.getClient(options)
    }

    override suspend fun detectFaces(mediaId: Long): List<DetectedFace> {
        val bitmap = decode(mediaId) ?: return emptyList()
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = detector.process(image).await()
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            faces.mapNotNull { face ->
                val box = face.boundingBox
                if (box.width() <= 10 || box.height() <= 10) return@mapNotNull null
                val bounds = RectF(
                    (box.left / w).coerceIn(0f, 1f),
                    (box.top / h).coerceIn(0f, 1f),
                    (box.right / w).coerceIn(0f, 1f),
                    (box.bottom / h).coerceIn(0f, 1f),
                )

                val padW = (box.width() * 0.15f).toInt()
                val padH = (box.height() * 0.15f).toInt()
                val cropLeft = maxOf(0, box.left - padW)
                val cropTop = maxOf(0, box.top - padH)
                val cropRight = minOf(bitmap.width, box.right + padW)
                val cropBottom = minOf(bitmap.height, box.bottom + padH)
                val cropW = cropRight - cropLeft
                val cropH = cropBottom - cropTop

                var feature: FloatArray? = null
                if (embedder.isAvailable && cropW > 16 && cropH > 16) {
                    val crop = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropW, cropH)
                    feature = embedder.embedBitmap(crop)
                    if (crop !== bitmap) crop.recycle()
                }
                if (feature == null) {
                    feature = FaceFeatureHelper.extractFeatures(bitmap, face)
                }

                DetectedFace(bounds, feature)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Face detection failed for media $mediaId", e)
            emptyList()
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun decode(mediaId: Long): Bitmap? = withContext(io) {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
        try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
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
        } catch (e: IOException) {
            null
        }
    }

    private companion object {
        const val TAG = "FaceScan"
        const val MAX_SIDE = 800
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
