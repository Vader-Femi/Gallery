package com.femi.gallery.domain.locked

import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel

/**
 * Encrypts and decrypts the contents of a locked file (a photo or video can be gigabytes) in segments, so that no
 * more than one segment is in memory and a video can be read from the middle. Each file has its own 32-byte key.
 */
interface ContentCipher {
    /** A stream that encrypts what is written to it into [out]; closing it writes the end of the ciphertext. */
    fun encrypt(fileKey: ByteArray, out: OutputStream): OutputStream

    /** A stream that decrypts the ciphertext read from [input], from its start. */
    fun decrypt(fileKey: ByteArray, input: InputStream): InputStream

    /**
     * A channel that decrypts the ciphertext in [channel] (positioned at the start of the ciphertext) and can be moved
     * to any position; its size is the size of the plaintext.
     */
    fun openSeekable(fileKey: ByteArray, channel: SeekableByteChannel): SeekableByteChannel
}
