package com.femi.gallery.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer

/**
 * Factory for creating [ExoPlayer] instances with resilient codec fallback.
 *
 * Automatically enables decoder fallback so that when a device or emulator lacks
 * hardware decoding support for a specific profile/level (or when hardware decoders
 * are exhausted), the player falls back to available secondary or software decoders.
 */
@OptIn(UnstableApi::class)
object PlayerFactory {

    fun createRenderersFactory(context: Context): DefaultRenderersFactory =
        DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
        }

    fun create(context: Context): ExoPlayer =
        ExoPlayer.Builder(context, createRenderersFactory(context)).build()
}
