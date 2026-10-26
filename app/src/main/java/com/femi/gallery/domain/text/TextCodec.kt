package com.femi.gallery.domain.text

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * The file format the recognised text is kept in: a small header, then for each photo its id, its
 * modified time and its text (a length and the UTF-8 bytes). Anything that does not look right
 * decodes to null, and the photos are simply read again.
 */
object TextCodec {
    private const val MAGIC = 0x47545854 // "GTXT"
    private const val VERSION = 1
    private const val HEADER_BYTES = 12
    private const val ENTRY_FIXED_BYTES = 20

    fun encode(entries: List<DetectedText>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(entries.size)
            for (entry in entries) {
                val text = entry.text.toByteArray(Charsets.UTF_8)
                out.writeLong(entry.id)
                out.writeLong(entry.modified)
                out.writeInt(text.size)
                out.write(text)
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): List<DetectedText>? {
        if (bytes.size < HEADER_BYTES) return null
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
                val count = input.readInt()
                if (count < 0 || count.toLong() * ENTRY_FIXED_BYTES > bytes.size) return null
                val entries = ArrayList<DetectedText>(count)
                repeat(count) {
                    val id = input.readLong()
                    val modified = input.readLong()
                    val length = input.readInt()
                    if (length < 0 || length > input.available()) return null
                    val text = ByteArray(length).also { input.readFully(it) }
                    entries.add(DetectedText(id, modified, String(text, Charsets.UTF_8)))
                }
                if (input.available() != 0) return null
                entries
            }
        } catch (e: IOException) {
            null
        }
    }
}
