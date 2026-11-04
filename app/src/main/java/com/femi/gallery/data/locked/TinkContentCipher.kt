package com.femi.gallery.data.locked

import com.femi.gallery.domain.locked.ContentCipher
import com.femi.gallery.domain.locked.LockedItemHeader
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The contents of locked files, encrypted with Tink's streaming AEAD (AES-256-GCM in 64 KiB segments, each with its
 * own nonce and tag, a key derived for the file with HKDF-SHA-256, and a mark on the last segment, so that a file cut
 * short is noticed). Every file has a key of its own.
 */
@Singleton
class TinkContentCipher @Inject constructor() : ContentCipher {

    private fun streaming(fileKey: ByteArray) =
        AesGcmHkdfStreaming(fileKey, "HmacSha256", KEY_BYTES, SEGMENT_BYTES, 0)

    override fun encrypt(fileKey: ByteArray, out: OutputStream): OutputStream =
        streaming(fileKey).newEncryptingStream(out, LockedItemHeader.STREAM_AAD)

    override fun decrypt(fileKey: ByteArray, input: InputStream): InputStream =
        streaming(fileKey).newDecryptingStream(input, LockedItemHeader.STREAM_AAD)

    override fun openSeekable(fileKey: ByteArray, channel: SeekableByteChannel): SeekableByteChannel =
        streaming(fileKey).newSeekableDecryptingChannel(channel, LockedItemHeader.STREAM_AAD)

    private companion object {
        const val KEY_BYTES = 32
        const val SEGMENT_BYTES = 64 * 1024
    }
}
