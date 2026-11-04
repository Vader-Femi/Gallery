package com.femi.gallery.domain.face

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class DetectedFace(
    val bounds: RectF,
    val feature: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DetectedFace) return false
        return bounds == other.bounds && feature.contentEquals(other.feature)
    }

    override fun hashCode(): Int {
        var result = bounds.hashCode()
        result = 31 * result + feature.contentHashCode()
        return result
    }
}

object FaceFeatureHelper {
    const val FEATURE_SIZE = 76 // 12 geometric + 64 appearance (4x4 grid * 4)

    fun FloatArray.toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in this) buffer.putFloat(f)
        return buffer.array()
    }

    fun ByteArray.toFloatArray(): FloatArray {
        if (isEmpty() || size % 4 != 0) return FloatArray(0)
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(size / 4)
        for (i in floats.indices) floats[i] = buffer.float
        return floats
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            val va = a[i]
            val vb = b[i]
            dot += va * vb
            normA += va * va
            normB += vb * vb
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 1e-6f) (dot / denom).coerceIn(-1f, 1f) else 0f
    }

    fun extractFeatures(bitmap: Bitmap, face: Face): FloatArray {
        val features = FloatArray(FEATURE_SIZE)
        val box = face.boundingBox

        // 1. Geometric proportions from landmarks (12 features)
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
        val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)?.position
        val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)?.position

        val boxW = max(1f, box.width().toFloat())
        val boxH = max(1f, box.height().toFloat())
        features[0] = (boxW / boxH).coerceIn(0.2f, 3.0f) // aspect ratio

        if (leftEye != null && rightEye != null) {
            val eyeDist = max(1f, dist(leftEye.x, leftEye.y, rightEye.x, rightEye.y))
            features[1] = (eyeDist / boxW).coerceIn(0f, 2f)

            val eyeMidX = (leftEye.x + rightEye.x) / 2f
            val eyeMidY = (leftEye.y + rightEye.y) / 2f

            if (nose != null) {
                features[2] = (dist(eyeMidX, eyeMidY, nose.x, nose.y) / eyeDist).coerceIn(0f, 3f)
                features[3] = ((nose.y - eyeMidY) / eyeDist).coerceIn(0f, 3f)
            }
            if (mouthBottom != null) {
                features[4] = (dist(eyeMidX, eyeMidY, mouthBottom.x, mouthBottom.y) / eyeDist).coerceIn(0f, 4f)
                if (nose != null) {
                    features[5] = (dist(nose.x, nose.y, mouthBottom.x, mouthBottom.y) / eyeDist).coerceIn(0f, 3f)
                }
            }
            if (mouthLeft != null && mouthRight != null) {
                features[6] = (dist(mouthLeft.x, mouthLeft.y, mouthRight.x, mouthRight.y) / eyeDist).coerceIn(0f, 3f)
            }
            if (leftCheek != null && rightCheek != null) {
                features[7] = (dist(leftCheek.x, leftCheek.y, rightCheek.x, rightCheek.y) / eyeDist).coerceIn(0f, 3f)
            }
            features[8] = ((eyeMidY - box.top) / boxH).coerceIn(0f, 1f)
            features[9] = face.headEulerAngleY / 90f // yaw
            features[10] = face.headEulerAngleZ / 90f // roll
            features[11] = face.headEulerAngleX / 90f // pitch
        }

        // 2. Appearance & Color features (4x4 spatial grid * 4 values = 64 features)
        val padW = (boxW * 0.15f).toInt()
        val padH = (boxH * 0.15f).toInt()
        val cropLeft = max(0, box.left - padW)
        val cropTop = max(0, box.top - padH)
        val cropRight = min(bitmap.width, box.right + padW)
        val cropBottom = min(bitmap.height, box.bottom + padH)

        val cropW = cropRight - cropLeft
        val cropH = cropBottom - cropTop

        if (cropW > 4 && cropH > 4) {
            val faceCrop = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropW, cropH)
            val scaled = Bitmap.createScaledBitmap(faceCrop, 32, 32, true)
            if (faceCrop !== bitmap && faceCrop !== scaled) faceCrop.recycle()

            val pixels = IntArray(32 * 32)
            scaled.getPixels(pixels, 0, 32, 0, 0, 32, 32)
            scaled.recycle()

            var outIdx = 12
            // 4x4 blocks of 8x8 pixels each
            for (by in 0 until 4) {
                for (bx in 0 until 4) {
                    var sumY = 0f
                    var sumY2 = 0f
                    var sumCb = 0f
                    var sumCr = 0f
                    var count = 0

                    for (py in by * 8 until (by + 1) * 8) {
                        for (px in bx * 8 until (bx + 1) * 8) {
                            val color = pixels[py * 32 + px]
                            val r = (color shr 16) and 0xFF
                            val g = (color shr 8) and 0xFF
                            val b = color and 0xFF

                            // YCbCr components normalized to 0..1
                            val yVal = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                            val cbVal = (-0.1687f * r - 0.3313f * g + 0.5f * b + 128f) / 255f
                            val crVal = (0.5f * r - 0.4187f * g - 0.0813f * b + 128f) / 255f

                            sumY += yVal
                            sumY2 += yVal * yVal
                            sumCb += cbVal
                            sumCr += crVal
                            count++
                        }
                    }

                    val meanY = sumY / count
                    val varY = (sumY2 / count) - (meanY * meanY)
                    val stdY = sqrt(max(0f, varY))
                    val meanCb = sumCb / count
                    val meanCr = sumCr / count

                    features[outIdx++] = meanY
                    features[outIdx++] = stdY
                    features[outIdx++] = meanCb
                    features[outIdx++] = meanCr
                }
            }
        }

        // 3. Zero-center and L2 normalize feature vector (removes global DC luminance & skin-tone bias)
        var sum = 0f
        for (f in features) sum += f
        val mean = sum / features.size
        var normSq = 0f
        for (i in features.indices) {
            features[i] -= mean
            normSq += features[i] * features[i]
        }
        val norm = sqrt(normSq)
        if (norm > 1e-6f) {
            for (i in features.indices) features[i] /= norm
        }

        return features
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }
}
