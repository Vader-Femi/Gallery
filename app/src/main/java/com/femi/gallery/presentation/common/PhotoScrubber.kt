package com.femi.gallery.presentation.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.femi.gallery.domain.privacy.JpegMetadataStripper
import com.femi.gallery.domain.privacy.PngMetadataStripper
import java.io.File
import java.io.IOException
import kotlin.math.max

/**
 * Makes the copy of a photo that private sharing hands to other apps: the same picture without the place it was taken,
 * the time, the camera and its serial numbers, the owner, the thumbnail, XMP, or the extra data of HDR and motion
 * photos. JPEG and PNG files are rewritten without those parts (the picture data is not touched, so there is no loss
 * of quality); a GIF has none of them and is copied; anything else (HEIC, AVIF, WebP, ...) is decoded and saved again
 * as a JPEG (a PNG when it has transparency), which carries no metadata. It never falls back to the original: if the
 * copy cannot be made it throws, and the caller shares nothing.
 */
internal object PhotoScrubber {
    private const val MAX_SIDE = 8192

    fun scrub(context: Context, uri: Uri, directory: File): File {
        val resolver = context.contentResolver
        val head = ByteArray(8)
        val count = resolver.openInputStream(uri)?.use { input -> readUpTo(input, head) } ?: throw IOException("Cannot open $uri")

        val isJpeg = count >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()
        val isPng = count == 8 && head.contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val isGif = count >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "GIF8"

        return when {
            isJpeg -> {
                // The orientation is the one thing worth keeping, so that the photo is not shown on its side.
                val orientation = runCatching {
                    resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }
                }.getOrNull() ?: 1
                write(directory, ".jpg") { out ->
                    resolver.openInputStream(uri)!!.use { JpegMetadataStripper.strip(it, out, orientation) }
                }
            }
            isPng -> write(directory, ".png") { out -> resolver.openInputStream(uri)!!.use { PngMetadataStripper.strip(it, out) } }
            isGif -> write(directory, ".gif") { out -> resolver.openInputStream(uri)!!.use { it.copyTo(out) } }
            else -> reencode(context, uri, directory)
        }
    }

    private fun reencode(context: Context, uri: Uri, directory: File): File {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
        }
        try {
            val png = bitmap.hasAlpha()
            return write(directory, if (png) ".png" else ".jpg") { out ->
                val ok = if (png) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) else bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                if (!ok) throw IOException("Cannot save the picture")
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Runs [writer] on a new file in [directory]; the file is deleted again if it fails. */
    private fun write(directory: File, extension: String, writer: (java.io.OutputStream) -> Unit): File {
        directory.mkdirs()
        val file = File.createTempFile("share_", extension, directory)
        try {
            file.outputStream().buffered().use { writer(it) }
            if (file.length() == 0L) throw IOException("Empty copy")
            return file
        } catch (e: Exception) {
            file.delete()
            throw if (e is IOException) e else IOException(e)
        }
    }

    private fun readUpTo(input: java.io.InputStream, into: ByteArray): Int {
        var total = 0
        while (total < into.size) {
            val n = input.read(into, total, into.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }
}
