package com.femi.gallery.domain.privacy

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Copies a JPEG without anything that is not the picture: it drops the Exif and XMP data (place, time, camera and
 * lens, serial numbers, owner, software, maker notes, the small thumbnail), the IPTC data, comments, the extra pictures
 * of a Multi-Picture file (the HDR gain map of Ultra HDR photos) and everything after the first picture (the video of
 * a motion photo). It keeps what the picture needs to look right: the colour profile, the Adobe colour marker and the
 * JFIF header. The only metadata written back is the orientation, as a minimal Exif block, so that the photo is not
 * shown on its side.
 */
object JpegMetadataStripper {
    private const val BUFFER = 64 * 1024

    /** [orientation] is the Exif orientation of the original (1 to 8); 1 (or anything else) writes no Exif at all. */
    fun strip(input: InputStream, output: OutputStream, orientation: Int = 1) {
        val source = if (input is BufferedInputStream) input else BufferedInputStream(input, BUFFER)
        val sink = BufferedOutputStream(output, BUFFER)
        if (source.read() != 0xFF || source.read() != 0xD8) throw IOException("Not a JPEG")
        sink.write(0xFF)
        sink.write(0xD8)

        var orientationPending = orientation in 2..8
        fun writeOrientationIfDue() {
            if (orientationPending) {
                sink.write(exifOrientationSegment(orientation))
                orientationPending = false
            }
        }

        var marker = nextMarker(source)
        while (true) {
            when {
                marker == 0xD9 -> {
                    writeOrientationIfDue()
                    sink.write(0xFF)
                    sink.write(0xD9)
                    break
                }
                marker == 0xDA -> {
                    writeOrientationIfDue()
                    writeSegment(source, sink, marker, body = null)
                    marker = copyEntropyData(source, sink)
                }
                marker in 0xD0..0xD7 || marker == 0x01 -> marker = nextMarker(source) // no length, no content
                else -> {
                    val length = (source.read() shl 8) or source.read()
                    if (length < 2) throw IOException("Broken JPEG segment")
                    val body = ByteArray(length - 2)
                    readFully(source, body)
                    if (keeps(marker, body)) {
                        // Exif goes right after the JFIF header (APP0) or, without one, before everything else.
                        if (marker != 0xE0) writeOrientationIfDue()
                        writeSegment(source, sink, marker, body)
                    }
                    marker = nextMarker(source)
                }
            }
        }
        sink.flush()
    }

    private fun keeps(marker: Int, body: ByteArray): Boolean = when (marker) {
        0xE0 -> startsWith(body, "JFIF\u0000")
        0xE2 -> startsWith(body, "ICC_PROFILE\u0000")
        0xEE -> startsWith(body, "Adobe")
        in 0xE1..0xEF, 0xFE -> false // Exif, XMP, the other application data, comments
        else -> true // the tables and the picture's own description
    }

    private fun startsWith(body: ByteArray, text: String): Boolean {
        if (body.size < text.length) return false
        for (i in text.indices) if (body[i].toInt() and 0xFF != text[i].code) return false
        return true
    }

    private fun writeSegment(source: InputStream, sink: OutputStream, marker: Int, body: ByteArray?) {
        sink.write(0xFF)
        sink.write(marker)
        if (body != null) {
            val length = body.size + 2
            sink.write(length shr 8)
            sink.write(length and 0xFF)
            sink.write(body)
        } else {
            // The scan header (SOS) is copied as it is.
            val high = source.read()
            val low = source.read()
            if (high < 0 || low < 0) throw EOFException("The JPEG ends too soon")
            val length = (high shl 8) or low
            if (length < 2) throw IOException("Broken JPEG segment")
            sink.write(high)
            sink.write(low)
            val header = ByteArray(length - 2)
            readFully(source, header)
            sink.write(header)
        }
    }

    /** Copies the compressed picture data of a scan; returns the marker that ends it (not yet written). */
    private fun copyEntropyData(source: InputStream, sink: OutputStream): Int {
        while (true) {
            val b = source.read()
            if (b < 0) throw EOFException("The JPEG ends too soon")
            if (b != 0xFF) {
                sink.write(b)
                continue
            }
            var next = source.read()
            while (next == 0xFF) next = source.read() // fill bytes
            if (next < 0) throw EOFException("The JPEG ends too soon")
            if (next == 0x00 || next in 0xD0..0xD7) { // a stuffed byte, or a restart marker: still the scan
                sink.write(0xFF)
                sink.write(next)
            } else {
                return next
            }
        }
    }

    private fun nextMarker(source: InputStream): Int {
        while (true) {
            val b = source.read()
            if (b < 0) throw EOFException("The JPEG ends too soon")
            if (b != 0xFF) continue
            var next = source.read()
            while (next == 0xFF) next = source.read()
            if (next < 0) throw EOFException("The JPEG ends too soon")
            if (next != 0x00) return next
        }
    }

    private fun readFully(source: InputStream, into: ByteArray) {
        var read = 0
        while (read < into.size) {
            val n = source.read(into, read, into.size - read)
            if (n < 0) throw EOFException("The JPEG ends too soon")
            read += n
        }
    }

    /** An Exif block that holds nothing but the orientation: tag 0x0112, one short value. */
    internal fun exifOrientationSegment(orientation: Int): ByteArray {
        val tiff = byteArrayOf(
            'M'.code.toByte(), 'M'.code.toByte(), 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, // big-endian header, first IFD at 8
            0x00, 0x01, // one entry
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, orientation.toByte(), 0x00, 0x00, // Orientation = SHORT 1
            0x00, 0x00, 0x00, 0x00, // no next IFD
        )
        val payload = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiff
        val length = payload.size + 2
        return byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), (length and 0xFF).toByte()) + payload
    }
}
