@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.media3.common

/** Stub for tools/verify, from androidx/media 1.11.1: only what the playback service reads. */
class MediaMetadata {
    val title: CharSequence? = null
}

interface Player {
    companion object {
        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4
    }

    interface Listener {
        fun onIsPlayingChanged(isPlaying: Boolean) {}
        fun onPlaybackStateChanged(playbackState: Int) {}
    }

    var playWhenReady: Boolean
    val isPlaying: Boolean
    val playbackState: Int
    val mediaMetadata: MediaMetadata
    val mediaItemCount: Int
    val currentMediaItem: MediaItem?
    val currentPosition: Long
    fun play()
    fun pause()
    fun release()
    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)
}
