package com.femi.gallery.domain.label

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

object LabelCodec {
    private const val MAGIC = 0x474C424C // "GLBL"
    private const val VERSION = 1
    private const val HEADER_BYTES = 12
    private const val ENTRY_FIXED_BYTES = 20

    fun encode(entries: List<DetectedLabel>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(entries.size)
            for (entry in entries) {
                val text = entry.labels.toByteArray(Charsets.UTF_8)
                out.writeLong(entry.id)
                out.writeLong(entry.modified)
                out.writeInt(text.size)
                out.write(text)
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): List<DetectedLabel>? {
        if (bytes.size < HEADER_BYTES) return null
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
                val count = input.readInt()
                if (count < 0 || count.toLong() * ENTRY_FIXED_BYTES > bytes.size) return null
                val entries = ArrayList<DetectedLabel>(count)
                repeat(count) {
                    val id = input.readLong()
                    val modified = input.readLong()
                    val length = input.readInt()
                    if (length < 0 || length > input.available()) return null
                    val text = ByteArray(length).also { input.readFully(it) }
                    entries.add(DetectedLabel(id, modified, String(text, Charsets.UTF_8)))
                }
                if (input.available() != 0) return null
                entries
            }
        } catch (e: IOException) {
            null
        }
    }
}
