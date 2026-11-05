@file:Suppress("unused", "UNUSED_PARAMETER")
package com.google.mlkit.vision.face

/** Stubs for tools/verify: the parts of ML Kit's face API that the face code names. */
class FaceLandmark private constructor() {
    val position: android.graphics.PointF = android.graphics.PointF()
    companion object {
        const val LEFT_EYE = 4
        const val RIGHT_EYE = 10
        const val LEFT_CHEEK = 1
        const val RIGHT_CHEEK = 7
        const val NOSE_BASE = 6
        const val MOUTH_LEFT = 5
        const val MOUTH_RIGHT = 11
        const val MOUTH_BOTTOM = 0
    }
}

class Face private constructor() {
    val boundingBox: android.graphics.Rect = android.graphics.Rect()
    val headEulerAngleX: Float = 0f
    val headEulerAngleY: Float = 0f
    val headEulerAngleZ: Float = 0f
    fun getLandmark(landmarkType: Int): FaceLandmark? = null
}
