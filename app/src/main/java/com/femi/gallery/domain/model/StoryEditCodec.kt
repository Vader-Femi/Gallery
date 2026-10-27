package com.femi.gallery.domain.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** The file format for the story edits: a few small records, written whole and read whole. */
object StoryEditCodec {
    private const val MAGIC = 0x53544544 // "STED"
    private const val VERSION = 1
    private const val NO_COVER = Long.MIN_VALUE

    fun encode(edits: List<StoryEdit>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(edits.size)
            for (edit in edits) {
                out.writeLong(edit.fromDay)
                out.writeLong(edit.toDay)
                out.writeBoolean(edit.title != null)
                if (edit.title != null) out.writeUTF(edit.title)
                out.writeLong(edit.coverId ?: NO_COVER)
            }
        }
        return bytes.toByteArray()
    }

    /** The edits, or null when [data] is not a valid file (wrong kind, newer version, cut short). */
    fun decode(data: ByteArray): List<StoryEdit>? = try {
        DataInputStream(ByteArrayInputStream(data)).use { input ->
            if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
            val count = input.readInt()
            if (count < 0 || count > MAX_EDITS) return null
            List(count) {
                val from = input.readLong()
                val to = input.readLong()
                val title = if (input.readBoolean()) input.readUTF() else null
                val cover = input.readLong()
                StoryEdit(from, to, title, cover.takeIf { it != NO_COVER })
            }
        }
    } catch (e: java.io.IOException) {
        null
    }

    private const val MAX_EDITS = 100_000
}
