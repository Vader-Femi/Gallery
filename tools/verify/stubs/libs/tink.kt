@file:Suppress("unused", "UNUSED_PARAMETER")
package com.google.crypto.tink.subtle

/** Stub for tools/verify; signatures checked against tink-java v1.24.0. */
class AesGcmHkdfStreaming(ikm: ByteArray, hkdfAlg: String, keySizeInBytes: Int, ciphertextSegmentSize: Int, firstSegmentOffset: Int) {
    fun newEncryptingStream(ciphertext: java.io.OutputStream, associatedData: ByteArray): java.io.OutputStream = ciphertext
    fun newDecryptingStream(ciphertextStream: java.io.InputStream, associatedData: ByteArray): java.io.InputStream = ciphertextStream
    fun newSeekableDecryptingChannel(ciphertextChannel: java.nio.channels.SeekableByteChannel, associatedData: ByteArray): java.nio.channels.SeekableByteChannel = ciphertextChannel
}
