package com.femi.gallery.data.exif

import android.content.Context
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
        if (item.isVideo) return@withContext MediaDetails(item)
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
}
