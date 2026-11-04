package com.femi.gallery.domain.locked

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Which key a locked file's header is sealed with. */
enum class LockedKeyKind(val code: Int) {
    /** The folder key of this phone, itself protected by the Android Keystore. */
    Folder(1),

    /** A key made from a passphrase: the form of a backup. */
    Passphrase(2),
    ;

    companion object {
        fun of(code: Int): LockedKeyKind? = entries.firstOrNull { it.code == code }
    }
}

/**
 * The start of a locked file: the key of that one file and its metadata, each sealed with the folder key (or, in a
 * backup, with the passphrase key). The photo or video itself follows the header as an encrypted stream made with the
 * file key. A backup is made by sealing the header again under another key; the stream is copied as it is.
 */
class LockedItemHeader(
    val kind: LockedKeyKind,
    private val sealedFileKey: ByteArray,
    private val sealedMeta: ByteArray,
) {
    /** Bytes the header takes at the start of the file. */
    val length: Int get() = MAGIC.size + 1 + 2 + sealedFileKey.size + 4 + sealedMeta.size

    fun write(out: OutputStream) {
        val data = DataOutputStream(out)
        data.write(MAGIC)
        data.writeByte(kind.code)
        data.writeShort(sealedFileKey.size)
        data.write(sealedFileKey)
        data.writeInt(sealedMeta.size)
        data.write(sealedMeta)
        data.flush()
    }

    fun toBytes(): ByteArray = java.io.ByteArrayOutputStream().also(::write).toByteArray()

    /** The key of the file itself. Throws a GeneralSecurityException when [key] is not the one it is sealed with. */
    fun openFileKey(key: ByteArray): ByteArray = LockedAead.open(key, sealedFileKey, aad("key", kind))

    fun openMeta(key: ByteArray): LockedMeta = LockedMeta.fromBytes(LockedAead.open(key, sealedMeta, aad("meta", kind)))

    /** The same file key and metadata, sealed with [toKey] as a [toKind] header. */
    fun resealed(fromKey: ByteArray, toKind: LockedKeyKind, toKey: ByteArray): LockedItemHeader =
        create(toKind, toKey, openFileKey(fromKey), openMeta(fromKey))

    companion object {
        val MAGIC = "GLK1".toByteArray(Charsets.US_ASCII)

        /** The associated data of the stream: constant, so that a header can be resealed without touching the stream. */
        val STREAM_AAD: ByteArray = MAGIC

        private const val MAX_SEALED_KEY = 1024
        private const val MAX_SEALED_META = 1 shl 20

        private fun aad(what: String, kind: LockedKeyKind): ByteArray = what.toByteArray(Charsets.US_ASCII) + byteArrayOf(kind.code.toByte())

        fun create(kind: LockedKeyKind, key: ByteArray, fileKey: ByteArray, meta: LockedMeta): LockedItemHeader =
            LockedItemHeader(
                kind = kind,
                sealedFileKey = LockedAead.seal(key, fileKey, aad("key", kind)),
                sealedMeta = LockedAead.seal(key, meta.toBytes(), aad("meta", kind)),
            )

        /** Reads a header from the start of [input], leaving it at the first byte of the stream. */
        fun read(input: InputStream): LockedItemHeader {
            val data = DataInputStream(input)
            val magic = ByteArray(MAGIC.size).also { data.readFully(it) }
            if (!magic.contentEquals(MAGIC)) throw IOException("Not a locked file")
            val kind = LockedKeyKind.of(data.readUnsignedByte()) ?: throw IOException("Unknown kind of locked file")
            val keyLength = data.readUnsignedShort()
            if (keyLength > MAX_SEALED_KEY) throw IOException("Broken header")
            val sealedKey = ByteArray(keyLength).also { data.readFully(it) }
            val metaLength = data.readInt()
            if (metaLength < 0 || metaLength > MAX_SEALED_META) throw IOException("Broken header")
            val sealedMeta = ByteArray(metaLength).also { data.readFully(it) }
            return LockedItemHeader(kind, sealedKey, sealedMeta)
        }
    }
}
