package com.femi.gallery.domain.locked

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** What is known about one locked photo or video; kept encrypted, inside the item's own file. */
data class LockedMeta(
    val id: Long,
    val name: String,
    val mimeType: String,
    val dateTakenMillis: Long,
    val dateAddedMillis: Long,
    val width: Int,
    val height: Int,
    /** Size of the original file in bytes. */
    val sizeBytes: Long,
    val durationMillis: Long,
    /** Where the file came from (e.g. "DCIM/Camera/"), so that unlocking can put it back there. */
    val relativePath: String,
) {
    fun toBytes(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.writeByte(VERSION)
            it.writeLong(id)
            it.writeUTF(name)
            it.writeUTF(mimeType)
            it.writeLong(dateTakenMillis)
            it.writeLong(dateAddedMillis)
            it.writeInt(width)
            it.writeInt(height)
            it.writeLong(sizeBytes)
            it.writeLong(durationMillis)
            it.writeUTF(relativePath)
        }
        return bytes.toByteArray()
    }

    companion object {
        private const val VERSION = 1

        fun fromBytes(bytes: ByteArray): LockedMeta {
            val input = DataInputStream(ByteArrayInputStream(bytes))
            val version = input.readUnsignedByte()
            require(version == VERSION) { "Unknown metadata version $version" }
            return LockedMeta(
                id = input.readLong(),
                name = input.readUTF(),
                mimeType = input.readUTF(),
                dateTakenMillis = input.readLong(),
                dateAddedMillis = input.readLong(),
                width = input.readInt(),
                height = input.readInt(),
                sizeBytes = input.readLong(),
                durationMillis = input.readLong(),
                relativePath = input.readUTF(),
            )
        }
    }
}
