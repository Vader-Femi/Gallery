@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.media3.effect

class FrameDropEffect private constructor() {
    companion object {
        fun createDefaultFrameDropEffect(targetFrameRate: Float): FrameDropEffect = FrameDropEffect()
    }
}

class ScaleAndRotateTransformation private constructor() : androidx.media3.common.Effect {
    class Builder {
        fun setRotationDegrees(degrees: Float): Builder = this
        fun build(): ScaleAndRotateTransformation = ScaleAndRotateTransformation()
    }
}
