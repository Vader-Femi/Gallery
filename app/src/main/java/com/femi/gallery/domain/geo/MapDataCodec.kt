package com.femi.gallery.domain.geo

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException

/**
 * Reads the map data files in app/src/main/assets/map (built by tools/build_map_data.py, which
 * documents the format). Damaged data decodes to null.
 */
object MapDataCodec {
    private const val POLYLINES_MAGIC = 0x47524E47 // "GRNG"
    private const val PLACES_MAGIC = 0x47504C43 // "GPLC"
    private const val VERSION = 1
    private const val SCALE = 10_000.0

    fun decodePolylines(bytes: ByteArray): List<MapPolyline>? = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != POLYLINES_MAGIC || input.readInt() != VERSION) return null
            val count = input.readInt()
            // Every polyline takes at least 12 bytes, which bounds what a damaged count can ask for.
            if (count < 0 || count > bytes.size / 12) return null
            List(count) {
                val points = input.readInt()
                if (points < 0 || points > bytes.size) throw IOException("Impossible point count")
                val xs = DoubleArray(points)
                val ys = DoubleArray(points)
                var lon = 0
                var lat = 0
                for (i in 0 until points) {
                    if (i == 0) {
                        lon = input.readInt()
                        lat = input.readInt()
                    } else {
                        lon += readVarint(input)
                        lat += readVarint(input)
                    }
                    xs[i] = Mercator.x(lon / SCALE)
                    ys[i] = Mercator.y(lat / SCALE)
                }
                MapPolyline(xs, ys)
            }
        }
    } catch (e: IOException) {
        null
    }

    /** The places, largest first. */
    fun decodePlaces(bytes: ByteArray): List<MapPlace>? = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != PLACES_MAGIC || input.readInt() != VERSION) return null
            val count = input.readInt()
            if (count < 0 || count > bytes.size / 14) return null
            List(count) {
                val lat = input.readInt()
                val lon = input.readInt()
                val population = input.readInt()
                val length = input.readUnsignedShort()
                val name = ByteArray(length).also { input.readFully(it) }
                MapPlace(String(name, Charsets.UTF_8), lat / SCALE, lon / SCALE, population)
            }.sortedByDescending { it.population }
        }
    } catch (e: IOException) {
        null
    }

    private fun readVarint(input: DataInputStream): Int {
        var result = 0
        var shift = 0
        while (true) {
            val byte = input.readUnsignedByte()
            result = result or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
            if (shift > 28) throw IOException("Varint too long")
        }
        return (result ushr 1) xor -(result and 1) // undo zigzag
    }

    // The encoders below exist for tests; the app only reads data that the script wrote.

    internal fun encodePolylines(lines: List<List<GeoPoint>>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(POLYLINES_MAGIC)
            out.writeInt(VERSION)
            out.writeInt(lines.size)
            for (line in lines) {
                out.writeInt(line.size)
                var lon = 0
                var lat = 0
                line.forEachIndexed { i, point ->
                    val pointLon = Math.round(point.longitude * SCALE).toInt()
                    val pointLat = Math.round(point.latitude * SCALE).toInt()
                    if (i == 0) {
                        out.writeInt(pointLon)
                        out.writeInt(pointLat)
                    } else {
                        writeVarint(out, pointLon - lon)
                        writeVarint(out, pointLat - lat)
                    }
                    lon = pointLon
                    lat = pointLat
                }
            }
        }
        return bytes.toByteArray()
    }

    internal fun encodePlaces(places: List<MapPlace>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(PLACES_MAGIC)
            out.writeInt(VERSION)
            out.writeInt(places.size)
            for (place in places) {
                out.writeInt(Math.round(place.latitude * SCALE).toInt())
                out.writeInt(Math.round(place.longitude * SCALE).toInt())
                out.writeInt(place.population)
                val name = place.name.toByteArray(Charsets.UTF_8)
                out.writeShort(name.size)
                out.write(name)
            }
        }
        return bytes.toByteArray()
    }

    private fun writeVarint(out: DataOutputStream, value: Int) {
        var zigzag = (value shl 1) xor (value shr 31)
        while (zigzag and 0x7F.inv() != 0) {
            out.writeByte((zigzag and 0x7F) or 0x80)
            zigzag = zigzag ushr 7
        }
        out.writeByte(zigzag)
    }
}
