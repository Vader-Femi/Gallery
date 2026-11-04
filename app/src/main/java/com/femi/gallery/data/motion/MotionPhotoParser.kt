package com.femi.gallery.data.motion

import java.nio.ByteBuffer
import java.nio.ByteOrder

object MotionPhotoParser {

    data class VideoOffset(
        val offset: Long,
        val length: Long,
        val presentationTimestampUs: Long = 0L,
    )

    fun parseXmp(xmp: String?, fileLength: Long): VideoOffset? {
        if (xmp == null || fileLength <= 0) return null
        if (!xmp.contains("MotionPhoto", ignoreCase = true) && !xmp.contains("MicroVideo", ignoreCase = true)) {
            return null
        }

        var ptsUs = 0L
        val ptsRegex = Regex("""(?:MotionPhotoPresentationTimestampUs|MicroVideoPresentationTimestampUs)="?(\d+)""", RegexOption.IGNORE_CASE)
        ptsRegex.find(xmp)?.let { match ->
            ptsUs = match.groupValues[1].toLongOrNull() ?: 0L
        }

        // Pattern 1: MicroVideoOffset
        val offsetRegex = Regex("""(?:GCamera:MicroVideoOffset|MicroVideoOffset)="?(\d+)""", RegexOption.IGNORE_CASE)
        offsetRegex.find(xmp)?.let { match ->
            val offsetFromEnd = match.groupValues[1].toLongOrNull() ?: 0L
            if (offsetFromEnd in 100 until fileLength) {
                val start = fileLength - offsetFromEnd
                return VideoOffset(offset = start, length = offsetFromEnd, presentationTimestampUs = ptsUs)
            }
        }

        // Pattern 2: Container Directory (Item:Length with Semantic="MotionPhoto" or Mime="video/mp4")
        val itemLengthRegex = Regex("""(?:Item:Semantic="MotionPhoto"|Item:Mime="video/mp4")[^>]*Item:Length="?(\d+)""", RegexOption.IGNORE_CASE)
        itemLengthRegex.find(xmp)?.let { match ->
            val itemLength = match.groupValues[1].toLongOrNull() ?: 0L
            if (itemLength in 100 until fileLength) {
                val start = fileLength - itemLength
                return VideoOffset(offset = start, length = itemLength, presentationTimestampUs = ptsUs)
            }
        }

        // Pattern 3: Container Directory where Item:Length comes before Item:Semantic
        val itemLengthBeforeRegex = Regex("""Item:Length="?(\d+)"?[^>]*(?:Item:Semantic="MotionPhoto"|Item:Mime="video/mp4")""", RegexOption.IGNORE_CASE)
        itemLengthBeforeRegex.find(xmp)?.let { match ->
            val itemLength = match.groupValues[1].toLongOrNull() ?: 0L
            if (itemLength in 100 until fileLength) {
                val start = fileLength - itemLength
                return VideoOffset(offset = start, length = itemLength, presentationTimestampUs = ptsUs)
            }
        }

        return null
    }

    private val VALID_BRANDS = setOf("mp41", "mp42", "isom", "iso2", "qt  ", "avc1", "M4V ", "MSNV")

    fun findFtypInBytes(searchBytes: ByteArray, searchLen: Int, baseOffset: Long): Long? {
        for (i in 0 until searchLen - 7) {
            if (searchBytes[i + 4] == 'f'.code.toByte() &&
                searchBytes[i + 5] == 't'.code.toByte() &&
                searchBytes[i + 6] == 'y'.code.toByte() &&
                searchBytes[i + 7] == 'p'.code.toByte()
            ) {
                if (i + 11 < searchLen) {
                    val brand = String(searchBytes, i + 8, 4, Charsets.US_ASCII)
                    if (brand in VALID_BRANDS || brand.startsWith("mp4") || brand.startsWith("iso")) {
                        return baseOffset + i
                    }
                }
            }
        }
        return null
    }

    fun parseSamsungSefDirectory(
        sefhBuf: ByteBuffer,
        sefhPos: Long,
        checkPayloadFtyp: (dataBlockPos: Long) -> Long?,
    ): VideoOffset? {
        if (sefhBuf.limit() < 12) return null
        sefhBuf.order(ByteOrder.LITTLE_ENDIAN)
        val sefh = sefhBuf.getInt(0)
        if (sefh != 0x48464553) return null // 'SEFH'

        val entryCount = sefhBuf.getInt(8)
        var offsetInDir = 12
        for (i in 0 until entryCount) {
            if (offsetInDir + 12 > sefhBuf.limit()) break
            val tagType = sefhBuf.getShort(offsetInDir + 2).toInt() and 0xFFFF
            val noff = sefhBuf.getInt(offsetInDir + 4).toLong() and 0xFFFFFFFFL
            val payloadLen = sefhBuf.getInt(offsetInDir + 8).toLong() and 0xFFFFFFFFL
            offsetInDir += 12

            // 0x0A30 is MotionPhoto_Data
            if (tagType == 0x0A30) {
                val dataBlockPos = sefhPos - noff
                val ftypStart = checkPayloadFtyp(dataBlockPos) ?: dataBlockPos
                val videoLen = if (payloadLen > (ftypStart - dataBlockPos)) {
                    payloadLen - (ftypStart - dataBlockPos)
                } else {
                    sefhPos - ftypStart
                }
                return VideoOffset(offset = ftypStart, length = videoLen)
            }
        }
        return null
    }
}
