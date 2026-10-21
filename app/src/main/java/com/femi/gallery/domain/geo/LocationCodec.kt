package com.femi.gallery.domain.geo

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.math.roundToInt

/**
 * The compact file format the scan results are kept in: a small header, then 25 bytes per photo
 * (id, modified, a "has a place" flag and the place as millionths of a degree, about 11 cm).
 * Anything that does not look right decodes to null, and the photos are simply scanned again.
 */
object LocationCodec {
    private const val MAGIC = 0x474C4F43 // "GLOC"
    private const val VERSION = 1
    private const val HEADER_BYTES = 12
    private const val ENTRY_BYTES = 25
    private const val SCALE = 1_000_000.0

    fun encode(entries: List<LocationEntry>): ByteArray {
        val bytes = ByteArrayOutputStream(HEADER_BYTES + entries.size * ENTRY_BYTES)
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(entries.size)
            for (entry in entries) {
                out.writeLong(entry.id)
                out.writeLong(entry.modified)
                val place = entry.place
                out.writeByte(if (place != null) 1 else 0)
                out.writeInt(if (place != null) (place.latitude * SCALE).roundToInt() else 0)
                out.writeInt(if (place != null) (place.longitude * SCALE).roundToInt() else 0)
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): List<LocationEntry>? {
        if (bytes.size < HEADER_BYTES) return null
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
                val count = input.readInt()
                if (count < 0 || bytes.size.toLong() != HEADER_BYTES + count.toLong() * ENTRY_BYTES) return null
                List(count) {
                    val id = input.readLong()
                    val modified = input.readLong()
                    val hasPlace = input.readByte().toInt() == 1
                    val latitude = input.readInt() / SCALE
                    val longitude = input.readInt() / SCALE
                    val place = GeoPoint(latitude, longitude)
                    LocationEntry(id, modified, if (hasPlace && place.isValid) place else null)
                }
            }
        } catch (e: IOException) {
            null
        }
    }
}
