@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.media3.common

/** Stub for tools/verify: the marker interface of Media3 effects. */
interface Effect

class MediaItem private constructor() {
    class ClippingConfiguration private constructor() {
        class Builder {
            fun setStartPositionMs(startPositionMs: Long): Builder = this
            fun setEndPositionMs(endPositionMs: Long): Builder = this
            fun setStartsAtKeyFrame(startsAtKeyFrame: Boolean): Builder = this
            fun build(): ClippingConfiguration = ClippingConfiguration()
        }
    }

    class Builder {
        fun setUri(uri: android.net.Uri?): Builder = this
        fun setClippingConfiguration(clippingConfiguration: ClippingConfiguration): Builder = this
        fun build(): MediaItem = MediaItem()
    }
}

object C {
    const val TIME_UNSET: Long = -Long.MAX_VALUE
}

class SpeedParameters(val speedProvider: androidx.media3.common.audio.SpeedProvider, val shouldMaintainPitch: Boolean = false)

