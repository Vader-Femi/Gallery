@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.media3.common

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
