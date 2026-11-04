package com.femi.gallery.domain.locked

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM for the small things: a file's key, its metadata, a key wrapped by another key. The result is the
 * random 12-byte nonce followed by the ciphertext and its 16-byte tag. Opening fails with a
 * [GeneralSecurityException] when the key, the associated data or a single bit of the sealed bytes is wrong.
 */
object LockedAead {
    const val KEY_BYTES = 32
    private const val NONCE_BYTES = 12
    const val OVERHEAD_BYTES = NONCE_BYTES + 16
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    fun newKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "A key is $KEY_BYTES bytes" }
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return nonce + cipher.doFinal(plaintext)
    }

    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "A key is $KEY_BYTES bytes" }
        if (sealed.size < OVERHEAD_BYTES) throw GeneralSecurityException("Sealed data is too short")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES))
        cipher.updateAAD(aad)
        return cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
    }
}
