package com.femi.gallery.domain.clip

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ClipCodec {
    private const val MAGIC = 0x47434C50 // "GCLP"
    private const val VERSION = 1
    private const val HEADER_BYTES = 12

    fun encode(entries: List<DetectedEmbedding>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(entries.size)
            for (entry in entries) {
                out.writeLong(entry.id)
                out.writeLong(entry.modified)
                out.writeInt(entry.embedding.size)
                val buffer = ByteBuffer.allocate(entry.embedding.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                buffer.asFloatBuffer().put(entry.embedding)
                out.write(buffer.array())
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): List<DetectedEmbedding>? {
        if (bytes.size < HEADER_BYTES) return null
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
                val count = input.readInt()
                if (count < 0 || count.toLong() * 20 > bytes.size) return null
                val entries = ArrayList<DetectedEmbedding>(count)
                repeat(count) {
                    val id = input.readLong()
                    val modified = input.readLong()
                    val dim = input.readInt()
                    if (dim < 0 || dim > 2048 || dim * 4 > input.available()) return null
                    val embBytes = ByteArray(dim * 4).also { input.readFully(it) }
                    val floatBuffer = ByteBuffer.wrap(embBytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                    val embedding = FloatArray(dim).also { floatBuffer.get(it) }
                    entries.add(DetectedEmbedding(id, modified, embedding))
                }
                if (input.available() != 0) return null
                entries
            }
        } catch (e: IOException) {
            null
        }
    }
}
