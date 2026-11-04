package com.femi.gallery.data.exif

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.model.MediaDetails
import com.femi.gallery.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExifReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun read(item: MediaItem): MediaDetails = withContext(io) {
        if (item.isVideo) return@withContext readVideoDetails(item)
        try {
            openStream(Uri.parse(item.uri))?.use { stream ->
                val exif = ExifInterface(stream)
                val latLong = exif.latLong
                MediaDetails(
                    item = item,
                    camera = cameraName(exif),
                    aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 },
                    exposureSeconds = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 },
                    iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 },
                    focalLengthMm = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 },
                    latitude = latLong?.get(0),
                    longitude = latLong?.get(1),
                )
            } ?: MediaDetails(item)
        } catch (e: Exception) {
            MediaDetails(item)
        }
    }

    /**
     * Asks for the unredacted file so GPS data is included. That needs ACCESS_MEDIA_LOCATION;
     * without it Android throws, and we fall back to the redacted stream.
     */
    private fun openStream(uri: Uri): InputStream? = try {
        context.contentResolver.openInputStream(MediaStore.setRequireOriginal(uri))
    } catch (e: Exception) {
        context.contentResolver.openInputStream(uri)
    }

    private fun cameraName(exif: ExifInterface): String? {
        val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.takeIf { it.isNotEmpty() }
        val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            model == null -> make
            make == null -> model
            model.startsWith(make, ignoreCase = true) -> model
            else -> "$make $model"
        }
    }

    private fun readVideoDetails(item: MediaItem): MediaDetails {
        val uri = Uri.parse(item.uri)
        var fps: Double? = null
        var lat: Double? = null
        var lon: Double? = null

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    val rate = runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble() }.getOrNull()
                        ?: runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE).toDouble() }.getOrNull()
                    if (rate != null && rate > 0.0) {
                        fps = rate
                        break
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            extractor.release()
        }

        try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)

                if (fps == null) {
                    val captureRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toDoubleOrNull()
                    if (captureRate != null && captureRate > 0.0) {
                        fps = captureRate
                    } else {
                        val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull()
                        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                            ?: item.durationMillis
                        if (frameCount != null && frameCount > 0 && durationMs > 0) {
                            fps = (frameCount * 1000.0) / durationMs
                        }
                    }
                }

                val locationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                if (locationStr != null) {
                    parseIso6709(locationStr)?.let { (latitude, longitude) ->
                        lat = latitude
                        lon = longitude
                    }
                }
            }
        } catch (_: Exception) {
        }

        return MediaDetails(
            item = item,
            fps = fps,
            latitude = lat,
            longitude = lon,
        )
    }

    private companion object {
        val ISO_6709_REGEX = Regex("([+-][0-9]+(?:\\.[0-9]+)?)([+-][0-9]+(?:\\.[0-9]+)?)")

        fun parseIso6709(location: String): Pair<Double, Double>? {
            val match = ISO_6709_REGEX.find(location) ?: return null
            val lat = match.groupValues[1].toDoubleOrNull() ?: return null
            val lon = match.groupValues[2].toDoubleOrNull() ?: return null
            return lat to lon
        }
    }
}
