package com.femi.gallery.data.locked

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Protects the folder key with a key that never leaves the phone's secure hardware. */
interface KeyEncryptor {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray
}

/**
 * An AES-256-GCM key in the Android Keystore (in StrongBox, the separate security chip, when the phone has one).
 * With [authBound] the key can only be used within [AUTH_VALIDITY_SECONDS] seconds after the person has
 * authenticated with a fingerprint, face, PIN, pattern or password; it is then deleted by Android for good if the
 * screen lock is removed.
 */
class AndroidKeystoreKeyEncryptor(
    private val alias: String,
    private val authBound: Boolean,
) : KeyEncryptor {

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    fun deleteKey() {
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun existing(): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply {
                if (authBound) {
                    setUserAuthenticationRequired(true)
                    setUserAuthenticationParameters(
                        AUTH_VALIDITY_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    )
                    // A new fingerprint must not destroy the key (and with it every locked photo).
                    setInvalidatedByBiometricEnrollment(false)
                }
                if (strongBox) setIsStrongBoxBacked(true)
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
            init(spec)
            generateKey()
        }
    }

    private fun keyForEncryption(): SecretKey = existing() ?: try {
        generate(strongBox = true)
    } catch (e: StrongBoxUnavailableException) {
        generate(strongBox = false)
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyForEncryption())
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        val key = existing() ?: throw UnrecoverableKeyException("The key $alias is gone")
        val ivLength = sealed[0].toInt() and 0xFF
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 1, ivLength))
        return cipher.doFinal(sealed, 1 + ivLength, sealed.size - 1 - ivLength)
    }

    companion object {
        const val AUTH_VALIDITY_SECONDS = 60
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
