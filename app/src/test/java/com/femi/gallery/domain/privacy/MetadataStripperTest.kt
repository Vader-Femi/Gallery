package com.femi.gallery.domain.privacy

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

private fun ByteArray.indexOf(text: String): Int {
    val needle = text.toByteArray(Charsets.ISO_8859_1)
    outer@ for (i in 0..size - needle.size) {
        for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
        return i
    }
    return -1
}

private fun ByteArray.contains(text: String) = indexOf(text) >= 0

/** One JPEG segment: FF, marker, the length (counting its own two bytes) and the body. */
private fun segment(marker: Int, body: ByteArray): ByteArray {
    val length = body.size + 2
    return bytes(0xFF, marker, length shr 8, length and 0xFF) + body
}

private fun ascii(text: String) = text.toByteArray(Charsets.ISO_8859_1)

class JpegMetadataStripperTest {
    private val jfif = segment(0xE0, ascii("JFIF\u0000") + ByteArray(9) { 1 })
    private val exif = segment(0xE1, ascii("Exif\u0000\u0000") + ascii("GPSLatitude secret place"))
    private val xmp = segment(0xE1, ascii("http://ns.adobe.com/xap/1.0/\u0000") + ascii("<x:xmpmeta>owner secret</x:xmpmeta>"))
    private val icc = segment(0xE2, ascii("ICC_PROFILE\u0000") + ByteArray(10) { 7 })
    private val mpf = segment(0xE2, ascii("MPF\u0000") + ByteArray(8) { 3 })
    private val photoshop = segment(0xED, ascii("Photoshop 3.0\u0000") + ascii("iptc secret"))
    private val comment = segment(0xFE, ascii("comment secret"))
    private val dqt = segment(0xDB, ByteArray(65) { 5 })
    private val sof = segment(0xC0, ByteArray(15) { 9 })
    private val dht = segment(0xC4, ByteArray(20) { 4 })
    private val sos = segment(0xDA, ByteArray(10) { 2 })
    /** Picture data with a stuffed byte (FF 00) and a restart marker (FF D0), as real data has. */
    private val scan = bytes(0x12, 0x34, 0xFF, 0x00, 0x56, 0xFF, 0xD0, 0x78, 0x9A)
    private val eoi = bytes(0xFF, 0xD9)
    private val soi = bytes(0xFF, 0xD8)
    private val trailer = soi + ascii("motion video secret trailing bytes")

    private fun run(input: ByteArray, orientation: Int = 1): ByteArray {
        val out = ByteArrayOutputStream()
        JpegMetadataStripper.strip(ByteArrayInputStream(input), out, orientation)
        return out.toByteArray()
    }

    private val full = soi + jfif + exif + xmp + icc + mpf + photoshop + comment + dqt + sof + dht + sos + scan + eoi + trailer

    @Test
    fun everythingThatTellsSomethingAboutThePhotoIsGone() {
        val result = run(full)
        for (secret in listOf("GPS", "secret", "xap", "xmpmeta", "MPF", "Photoshop", "Exif")) {
            assertFalse("still contains $secret", result.contains(secret))
        }
    }

    @Test
    fun thePictureAndItsColourProfileAreKeptExactly() {
        val result = run(full)
        assertArrayEquals(soi + jfif + icc + dqt + sof + dht + sos + scan + eoi, result)
    }

    @Test
    fun nothingComesAfterTheEndOfThePicture() {
        val result = run(full)
        assertEquals(0xD9, result.last().toInt() and 0xFF)
        assertEquals(0xFF, result[result.size - 2].toInt() and 0xFF)
        assertFalse(result.contains("motion"))
    }

    @Test
    fun theOrientationIsKeptAsAMinimalExifBlockAfterTheJfifHeader() {
        val result = run(full, orientation = 6)
        val expected = soi + jfif + JpegMetadataStripper.exifOrientationSegment(6) + icc + dqt + sof + dht + sos + scan + eoi
        assertArrayEquals(expected, result)
        // The block is only the orientation: no place, no time.
        assertTrue(result.contains("Exif"))
        assertFalse(result.contains("GPS"))
        val block = JpegMetadataStripper.exifOrientationSegment(6)
        assertEquals(34, block.size - 2) // the length field counts itself and what follows
        // Header "MM" 002A, first IFD at 8, one entry: tag 0112, type 3 (short), count 1, the value 0006.
        val tiff = block.indexOf("MM")
        assertArrayEquals(bytes(0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, 0x00, 0x01), block.copyOfRange(tiff, tiff + 10))
        assertArrayEquals(bytes(0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x06), block.copyOfRange(tiff + 10, tiff + 20))
    }

    @Test
    fun anOrientationOfNormalWritesNoExifAtAll() {
        for (orientation in listOf(0, 1, 9, -3)) assertFalse(run(full, orientation).contains("Exif"))
    }

    @Test
    fun theOrientationBlockComesFirstWhenThereIsNoJfifHeader() {
        val result = run(soi + exif + dqt + sof + sos + scan + eoi, orientation = 3)
        assertArrayEquals(soi + JpegMetadataStripper.exifOrientationSegment(3) + dqt + sof + sos + scan + eoi, result)
    }

    @Test
    fun aProgressivePictureKeepsEveryScan() {
        val second = bytes(0xAB, 0xCD, 0xFF, 0x00)
        val progressive = soi + exif + dqt + sof + dht + sos + scan + dht + sos + second + eoi + trailer
        assertArrayEquals(soi + dqt + sof + dht + sos + scan + dht + sos + second + eoi, run(progressive))
    }

    @Test
    fun aPictureWithoutAnyMetadataComesOutTheSame() {
        val plain = soi + dqt + sof + dht + sos + scan + eoi
        assertArrayEquals(plain, run(plain))
    }

    @Test
    fun somethingThatIsNotAJpegIsRefused() {
        for (junk in listOf(ByteArray(0), ascii("not a picture at all"), bytes(0x89, 0x50, 0x4E, 0x47))) {
            try {
                run(junk)
                fail("Expected an IOException")
            } catch (e: IOException) {
                // expected
            }
        }
    }

    @Test
    fun aPictureThatWasCutShortIsRefusedNotShared() {
        for (cut in listOf(full.size - trailer.size - 1, soi.size + jfif.size + 3, full.size - trailer.size - scan.size - 5)) {
            try {
                run(full.copyOf(cut))
                fail("Expected an IOException for a cut at $cut")
            } catch (e: IOException) {
                // expected
            }
        }
    }
}

class PngMetadataStripperTest {
    private val signature = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private fun chunk(type: String, content: ByteArray): ByteArray {
        val n = content.size
        return bytes(n shr 24, n shr 16, n shr 8, n) + ascii(type) + content + bytes(1, 2, 3, 4)
    }

    private fun run(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        PngMetadataStripper.strip(ByteArrayInputStream(input), out)
        return out.toByteArray()
    }

    private val ihdr = chunk("IHDR", ByteArray(13) { 1 })
    private val gama = chunk("gAMA", ByteArray(4) { 2 })
    private val text = chunk("tEXt", ascii("Comment\u0000secret place"))
    private val xmp = chunk("iTXt", ascii("XML:com.adobe.xmp\u0000owner secret"))
    private val exif = chunk("eXIf", ascii("GPS secret"))
    private val time = chunk("tIME", ByteArray(7) { 3 })
    private val unknown = chunk("prVt", ascii("private secret"))
    private val idat = chunk("IDAT", ByteArray(30) { 8 })
    private val iend = chunk("IEND", ByteArray(0))

    @Test
    fun textExifTimeAndUnknownChunksAreDroppedTheRestIsKept() {
        val result = run(signature + ihdr + text + gama + xmp + exif + time + unknown + idat + iend + ascii("trailing secret"))
        assertArrayEquals(signature + ihdr + gama + idat + iend, result)
        assertFalse(result.contains("secret"))
    }

    @Test
    fun anAnimationAndTransparencyAreKept() {
        val extra = chunk("acTL", ByteArray(8)) + chunk("tRNS", ByteArray(3)) + chunk("fdAT", ByteArray(12))
        val input = signature + ihdr + extra + idat + iend
        assertArrayEquals(input, run(input))
    }

    @Test
    fun somethingThatIsNotAPngOrIsCutShortIsRefused() {
        for (junk in listOf(ByteArray(0), ascii("GIF89a....."), signature + ihdr.copyOf(10), signature + ihdr + idat)) {
            try {
                run(junk)
                fail("Expected an IOException")
            } catch (e: IOException) {
                // expected
            }
        }
    }
}
