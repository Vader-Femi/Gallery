package com.femi.gallery.domain.privacy

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Copies a PNG without its text chunks (which can hold the XMP data), the Exif chunk, the modification time and any
 * chunk it does not know. The picture, the colour information and the transparency stay, and so does an animation.
 */
object PngMetadataStripper {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /** Chunks that are not required but belong to the picture: colour, size, transparency, animation. */
    private val KEPT_OPTIONAL = setOf(
        "tRNS", "cHRM", "gAMA", "iCCP", "sBIT", "sRGB", "bKGD", "hIST", "pHYs", "sPLT", "cICP", "mDCv", "cLLi", "acTL", "fcTL", "fdAT",
    )

    fun strip(input: InputStream, output: OutputStream) {
        val data = DataInputStream(input)
        val signature = ByteArray(SIGNATURE.size)
        try {
            data.readFully(signature)
        } catch (e: EOFException) {
            throw IOException("Not a PNG")
        }
        if (!signature.contentEquals(SIGNATURE)) throw IOException("Not a PNG")
        output.write(signature)

        while (true) {
            val length = try {
                data.readInt()
            } catch (e: EOFException) {
                throw IOException("The PNG ends too soon")
            }
            if (length < 0) throw IOException("Broken PNG chunk")
            val type = ByteArray(4)
            val content = ByteArray(length)
            val crc = ByteArray(4)
            try {
                data.readFully(type)
                data.readFully(content)
                data.readFully(crc)
            } catch (e: EOFException) {
                throw IOException("The PNG ends too soon")
            }
            val name = String(type, Charsets.ISO_8859_1)
            val critical = type[0] in 'A'.code.toByte()..'Z'.code.toByte()
            if (critical || name in KEPT_OPTIONAL) {
                output.write(byteArrayOf((length shr 24).toByte(), (length shr 16).toByte(), (length shr 8).toByte(), length.toByte()))
                output.write(type)
                output.write(content)
                output.write(crc)
            }
            if (name == "IEND") break
        }
        output.flush()
    }
}
