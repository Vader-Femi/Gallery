package com.femi.gallery.domain.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object FaceAligner {
    const val TARGET_SIZE = 112

    /**
     * Aligns a detected face into a standardized 112x112 face crop using eye landmarks.
     * Eyes are rotated to be horizontal and scaled to standard inter-pupillary distance.
     */
    fun align(bitmap: Bitmap, face: Face): Bitmap {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position

        if (leftEye != null && rightEye != null) {
            val dx = rightEye.x - leftEye.x
            val dy = rightEye.y - leftEye.y
            val eyeDist = hypot(dx, dy)
            if (eyeDist > 4f) {
                val angle = atan2(dy, dx) * (180f / Math.PI.toFloat())
                val eyeCenterX = (leftEye.x + rightEye.x) / 2f
                val eyeCenterY = (leftEye.y + rightEye.y) / 2f

                // In standard 112x112 ArcFace alignment, eye distance is ~36px and eye center is at (56, 48)
                val scale = 36.0f / eyeDist

                val matrix = Matrix().apply {
                    postTranslate(-eyeCenterX, -eyeCenterY)
                    postRotate(-angle)
                    postScale(scale, scale)
                    postTranslate(56f, 48f)
                }

                val aligned = Bitmap.createBitmap(TARGET_SIZE, TARGET_SIZE, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(aligned)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                canvas.drawBitmap(bitmap, matrix, paint)
                return aligned
            }
        }

        // Fallback if landmarks not found: crop bounding box with padding
        val box = face.boundingBox
        val padW = (box.width() * 0.15f).toInt()
        val padH = (box.height() * 0.15f).toInt()
        val cropLeft = max(0, box.left - padW)
        val cropTop = max(0, box.top - padH)
        val cropRight = min(bitmap.width, box.right + padW)
        val cropBottom = min(bitmap.height, box.bottom + padH)
        val cropW = max(1, cropRight - cropLeft)
        val cropH = max(1, cropBottom - cropTop)

        val crop = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropW, cropH)
        val scaled = Bitmap.createScaledBitmap(crop, TARGET_SIZE, TARGET_SIZE, true)
        if (crop !== scaled && crop !== bitmap) crop.recycle()
        return scaled
    }

    /**
     * Checks whether the face has sufficient quality (size, pose) to be clustered reliably.
     */
    fun isReliable(face: Face): Boolean {
        val box = face.boundingBox
        if (box.width() < 36 || box.height() < 36) return false
        // Exclude extreme side-profiles
        if (Math.abs(face.headEulerAngleY) > 35f) return false
        if (Math.abs(face.headEulerAngleX) > 30f) return false
        return true
    }
}
