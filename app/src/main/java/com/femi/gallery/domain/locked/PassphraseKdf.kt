package com.femi.gallery.domain.locked

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Turns the passphrase of a backup into a 256-bit key; slow on purpose, so that guessing passphrases is costly. */
object PassphraseKdf {
    /** The number of rounds for a new backup (OWASP's figure for PBKDF2 with HMAC-SHA-256 in 2023). */
    const val DEFAULT_ITERATIONS = 600_000

    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, LockedAead.KEY_BYTES * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

/** What passphrase is good enough for a backup. */
object PassphrasePolicy {
    const val MIN_LENGTH = 12

    fun isAcceptable(passphrase: CharSequence): Boolean {
        val points = passphrase.codePoints().toArray()
        return points.size >= MIN_LENGTH && points.distinct().size >= 4
    }
}
