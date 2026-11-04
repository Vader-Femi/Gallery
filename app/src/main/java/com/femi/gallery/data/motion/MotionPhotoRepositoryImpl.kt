package com.femi.gallery.data.motion

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MotionPhotoInfo
import com.femi.gallery.domain.repository.MotionPhotoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MotionPhotoRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : MotionPhotoRepository {

    override suspend fun getMotionPhoto(item: MediaItem): MotionPhotoInfo? = withContext(io) {
        if (item.isVideo) return@withContext null

        // 1. Check for Apple Live Photo (paired .MOV/.MP4 video in same folder with same base name)
        findAppleLivePhoto(item)?.let { return@withContext it }

        // 2. Check for Embedded Motion Photo (Google Pixel, Samsung Galaxy, Xiaomi, etc.)
        findEmbeddedMotionPhoto(item)
    }

    private fun findAppleLivePhoto(item: MediaItem): MotionPhotoInfo? {
        val baseName = item.displayName.substringBeforeLast('.', item.displayName).ifEmpty { return null }
        val candidateExtensions = listOf("MOV", "mov", "MP4", "mp4")
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

        for (ext in candidateExtensions) {
            val targetName = "$baseName.$ext"
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = if (item.relativePath.isNotEmpty()) {
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            } else {
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            }
            val selectionArgs = if (item.relativePath.isNotEmpty()) {
                arrayOf(item.relativePath, targetName)
            } else {
                arrayOf(targetName)
            }
            resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val uri = ContentUris.withAppendedId(collection, id).toString()
                    return MotionPhotoInfo(videoUri = uri)
                }
            }
        }
        return null
    }

    private fun findEmbeddedMotionPhoto(item: MediaItem): MotionPhotoInfo? {
        val uri = Uri.parse(item.uri)
        val fileLength = getFileLength(uri)
        if (fileLength <= 50 * 1024) return null // Too small for image + video

        // A. Parse XMP metadata (Google Camera & modern Samsung)
        val xmpOffset = parseXmpVideoOffset(uri, fileLength)
        if (xmpOffset != null) {
            val cacheFile = extractSliceToCache(item, uri, xmpOffset.offset, xmpOffset.length)
            if (cacheFile != null) {
                return MotionPhotoInfo(
                    videoUri = Uri.fromFile(cacheFile).toString(),
                    presentationTimestampUs = xmpOffset.presentationTimestampUs,
                )
            }
        }

        // B. Parse Samsung SEF (Samsung Extensible Format) trailer
        val sefOffset = parseSamsungSef(uri, fileLength)
        if (sefOffset != null) {
            val cacheFile = extractSliceToCache(item, uri, sefOffset.offset, sefOffset.length)
            if (cacheFile != null) {
                return MotionPhotoInfo(videoUri = Uri.fromFile(cacheFile).toString())
            }
        }

        // C. Fallback: Search backwards for embedded MP4 'ftyp' box
        val ftypOffset = findFtypOffset(uri, fileLength)
        if (ftypOffset != null) {
            val length = fileLength - ftypOffset
            val cacheFile = extractSliceToCache(item, uri, ftypOffset, length)
            if (cacheFile != null) {
                return MotionPhotoInfo(videoUri = Uri.fromFile(cacheFile).toString())
            }
        }

        return null
    }

    private fun getFileLength(uri: Uri): Long = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
    }.getOrDefault(0L)

    private fun parseXmpVideoOffset(uri: Uri, fileLength: Long): MotionPhotoParser.VideoOffset? {
        val xmp = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttribute(ExifInterface.TAG_XMP)
            }
        }.getOrNull() ?: return null

        return MotionPhotoParser.parseXmp(xmp, fileLength)
    }

    private fun parseSamsungSef(uri: Uri, fileLength: Long): MotionPhotoParser.VideoOffset? {
        if (fileLength < 1024) return null
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val channel = FileInputStream(pfd.fileDescriptor).channel
                val tailBuf = ByteBuffer.allocate(1024)
                tailBuf.order(ByteOrder.LITTLE_ENDIAN)
                channel.position(maxOf(0L, fileLength - 1024))
                channel.read(tailBuf)
                tailBuf.flip()

                val limit = tailBuf.limit()
                if (limit < 8) return@use null
                val seft = tailBuf.getInt(limit - 4)
                // 'SEFT' in little endian: 0x54464553
                if (seft != 0x54464553) return@use null

                val sefSize = tailBuf.getInt(limit - 8)
                if (sefSize <= 0 || sefSize > fileLength - 8) return@use null

                val sefhPos = fileLength - 8 - sefSize
                val sefhBuf = ByteBuffer.allocate(minOf(sefSize + 8, 4096))
                channel.position(sefhPos)
                channel.read(sefhBuf)
                sefhBuf.flip()

                MotionPhotoParser.parseSamsungSefDirectory(sefhBuf, sefhPos) { dataBlockPos ->
                    val checkBuf = ByteBuffer.allocate(64)
                    channel.position(dataBlockPos)
                    channel.read(checkBuf)
                    checkBuf.flip()
                    for (b in 0 until checkBuf.limit() - 4) {
                        if (checkBuf.get(b) == 'f'.code.toByte() &&
                            checkBuf.get(b + 1) == 't'.code.toByte() &&
                            checkBuf.get(b + 2) == 'y'.code.toByte() &&
                            checkBuf.get(b + 3) == 'p'.code.toByte()
                        ) {
                            return@parseSamsungSefDirectory dataBlockPos + b - 4
                        }
                    }
                    null
                }
            }
        }.getOrNull()
    }

    private fun findFtypOffset(uri: Uri, fileLength: Long): Long? {
        val maxScan = minOf(fileLength, 32L * 1024 * 1024)
        val startScan = fileLength - maxScan
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val channel = FileInputStream(pfd.fileDescriptor).channel
                channel.position(startScan)
                val buffer = ByteArray(64 * 1024)
                var currentPos = startScan
                val prevOverlap = ByteArray(8)
                var prevOverlapLen = 0

                while (currentPos < fileLength) {
                    val toRead = minOf(buffer.size.toLong(), fileLength - currentPos).toInt()
                    val read = channel.read(ByteBuffer.wrap(buffer, 0, toRead))
                    if (read <= 0) break

                    val searchBytes = if (prevOverlapLen > 0) {
                        val combined = ByteArray(prevOverlapLen + read)
                        System.arraycopy(prevOverlap, 0, combined, 0, prevOverlapLen)
                        System.arraycopy(buffer, 0, combined, prevOverlapLen, read)
                        combined
                    } else {
                        buffer
                    }
                    val searchLen = if (prevOverlapLen > 0) prevOverlapLen + read else read

                    val matched = MotionPhotoParser.findFtypInBytes(
                        searchBytes = searchBytes,
                        searchLen = searchLen,
                        baseOffset = currentPos - prevOverlapLen,
                    )
                    if (matched != null) return@use matched

                    prevOverlapLen = minOf(8, read)
                    System.arraycopy(buffer, read - prevOverlapLen, prevOverlap, 0, prevOverlapLen)
                    currentPos += read
                }
                null
            }
        }.getOrNull()
    }

    private fun extractSliceToCache(item: MediaItem, uri: Uri, offset: Long, length: Long): File? {
        if (offset < 0 || length <= 0) return null
        val cacheDir = File(context.cacheDir, "motion_photos").apply { mkdirs() }
        val cacheFile = File(cacheDir, "motion_${item.id}_${item.sizeBytes}.mp4")

        if (cacheFile.exists() && cacheFile.length() == length) {
            return cacheFile
        }

        return runCatching {
            val temp = File(cacheDir, "temp_${System.nanoTime()}.mp4")
            val pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
            if (pfd != null) {
                pfd.use { fd ->
                    FileInputStream(fd.fileDescriptor).channel.use { inChannel ->
                        temp.outputStream().channel.use { outChannel ->
                            inChannel.position(offset)
                            var transferred = 0L
                            while (transferred < length) {
                                val count = inChannel.transferTo(offset + transferred, length - transferred, outChannel)
                                if (count <= 0) break
                                transferred += count
                            }
                        }
                    }
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    var skipped = 0L
                    while (skipped < offset) {
                        val s = input.skip(offset - skipped)
                        if (s <= 0) {
                            val dummy = ByteArray(minOf(4096L, offset - skipped).toInt())
                            val r = input.read(dummy)
                            if (r <= 0) break
                            skipped += r
                        } else {
                            skipped += s
                        }
                    }
                    if (skipped < offset) return@use null

                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var remaining = length
                        while (remaining > 0) {
                            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                            val read = input.read(buffer, 0, toRead)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            remaining -= read
                        }
                    }
                }
            }
            if (temp.length() == length) {
                temp.renameTo(cacheFile)
                cacheFile
            } else {
                temp.delete()
                null
            }
        }.getOrNull()
    }
}
