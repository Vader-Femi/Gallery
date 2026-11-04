package com.femi.gallery.domain.locked

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom

/** The passphrase does not open the backup. */
class WrongPassphraseException : GeneralSecurityException("Wrong passphrase")

/** The backup is damaged or incomplete. */
class BackupCorruptException(message: String) : IOException(message)

/**
 * The file of an encrypted backup of the Locked folder:
 *
 *     "GLB1" | rounds | salt | a sealed check value        (proves the passphrase is right)
 *     { 1 | length | one locked file with a passphrase header }...
 *     0 | a sealed count and total                          (proves nothing was cut off)
 *
 * The files inside are the locked files as they are on the phone, except that their headers are sealed with the key
 * made from the passphrase; their contents are copied without being decrypted.
 */
object LockedBackupFormat {
    private val MAGIC = "GLB1".toByteArray(Charsets.US_ASCII)
    private val CHECK_VALUE = "gallery locked backup".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val MAX_ITERATIONS = 10_000_000
    private const val MAX_SEALED = 256

    private fun aad(what: String): ByteArray = ("backup " + what).toByteArray(Charsets.US_ASCII)

    private fun trailer(count: Int, total: Long): ByteArray =
        ByteBuffer.allocate(12).putInt(count).putLong(total).array()

    class Writer(
        private val out: OutputStream,
        passphrase: CharArray,
        iterations: Int = PassphraseKdf.DEFAULT_ITERATIONS,
    ) {
        /** The key made from the passphrase, to seal the headers of the files that are added. */
        val key: ByteArray

        private var count = 0
        private var total = 0L

        init {
            val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
            key = PassphraseKdf.deriveKey(passphrase, salt, iterations)
            val data = DataOutputStream(out)
            data.write(MAGIC)
            data.writeInt(iterations)
            data.write(salt)
            val check = LockedAead.seal(key, CHECK_VALUE, aad("check"))
            data.writeShort(check.size)
            data.write(check)
            data.flush()
        }

        /** Adds one file of exactly [length] bytes, written by [write]. */
        fun addEntry(length: Long, write: (OutputStream) -> Unit) {
            require(length >= 0) { "Negative length" }
            val data = DataOutputStream(out)
            data.writeByte(1)
            data.writeLong(length)
            data.flush()
            val counting = CountingOutputStream(out)
            write(counting)
            check(counting.count == length) { "Wrote ${counting.count} bytes of an entry of $length" }
            count++
            total += length
        }

        fun finish() {
            val data = DataOutputStream(out)
            data.writeByte(0)
            val sealed = LockedAead.seal(key, trailer(count, total), aad("end"))
            data.writeShort(sealed.size)
            data.write(sealed)
            data.flush()
            out.flush()
        }
    }

    class Reader(private val input: InputStream, passphrase: CharArray) {
        /** The key made from the passphrase, to open the headers of the files in the backup. */
        val key: ByteArray

        private var current: LimitedInputStream? = null
        private var count = 0
        private var total = 0L
        private var ended = false

        init {
            val data = DataInputStream(input)
            try {
                val magic = ByteArray(MAGIC.size).also { data.readFully(it) }
                if (!magic.contentEquals(MAGIC)) throw BackupCorruptException("Not a backup of the Locked folder")
                val iterations = data.readInt()
                if (iterations < 1 || iterations > MAX_ITERATIONS) throw BackupCorruptException("Broken backup header")
                val salt = ByteArray(SALT_BYTES).also { data.readFully(it) }
                val checkSize = data.readUnsignedShort()
                if (checkSize > MAX_SEALED) throw BackupCorruptException("Broken backup header")
                val check = ByteArray(checkSize).also { data.readFully(it) }
                key = PassphraseKdf.deriveKey(passphrase, salt, iterations)
                val opened = try {
                    LockedAead.open(key, check, aad("check"))
                } catch (e: GeneralSecurityException) {
                    throw WrongPassphraseException()
                }
                if (!opened.contentEquals(CHECK_VALUE)) throw WrongPassphraseException()
            } catch (e: EOFException) {
                throw BackupCorruptException("The backup ends too soon")
            }
        }

        /**
         * The next file of the backup, to be read to its end (or left: it is skipped by the next call), or null after
         * the last one. Throws [BackupCorruptException] when the backup was cut short or changed.
         */
        fun nextEntry(): InputStream? {
            current?.skipRest()
            current = null
            if (ended) return null
            val data = DataInputStream(input)
            try {
                when (val marker = input.read()) {
                    -1 -> throw BackupCorruptException("The backup ends too soon")
                    0 -> {
                        val size = data.readUnsignedShort()
                        if (size > MAX_SEALED) throw BackupCorruptException("Broken end of backup")
                        val sealed = ByteArray(size).also { data.readFully(it) }
                        val opened = try {
                            LockedAead.open(key, sealed, aad("end"))
                        } catch (e: GeneralSecurityException) {
                            throw BackupCorruptException("The end of the backup was changed")
                        }
                        if (!opened.contentEquals(trailer(count, total))) {
                            throw BackupCorruptException("Files are missing from the backup")
                        }
                        ended = true
                        return null
                    }
                    1 -> {
                        val length = data.readLong()
                        if (length < 0) throw BackupCorruptException("Broken backup entry")
                        count++
                        total += length
                        return LimitedInputStream(input, length).also { current = it }
                    }
                    else -> throw BackupCorruptException("Broken backup entry marker $marker")
                }
            } catch (e: EOFException) {
                throw BackupCorruptException("The backup ends too soon")
            }
        }
    }

    private class CountingOutputStream(private val out: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    private class LimitedInputStream(private val input: InputStream, private var remaining: Long) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val b = input.read()
            if (b < 0) throw BackupCorruptException("The backup ends too soon")
            remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            if (len == 0) return 0
            val n = input.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (n < 0) throw BackupCorruptException("The backup ends too soon")
            remaining -= n
            return n
        }

        fun skipRest() {
            val sink = ByteArray(8192)
            while (remaining > 0) {
                if (read(sink, 0, sink.size) < 0) break
            }
        }
    }
}
