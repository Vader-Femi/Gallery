@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.media3.effect

class FrameDropEffect private constructor() {
    companion object {
        fun createDefaultFrameDropEffect(targetFrameRate: Float): FrameDropEffect = FrameDropEffect()
    }
}
