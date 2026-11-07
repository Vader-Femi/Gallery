package com.femi.gallery.presentation.common

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.femi.gallery.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShareEntryPoint {
    val settingsRepository: SettingsRepository
}

/** Shares one or more photos/videos through the system share sheet, respecting private sharing settings. */
fun shareUris(context: Context, uris: List<Uri>, mimeType: String? = null) {
    if (uris.isEmpty()) return

    val entryPoint = runCatching {
        EntryPointAccessors.fromApplication(context.applicationContext, ShareEntryPoint::class.java)
    }.getOrNull()

    val isPrivate = entryPoint?.settingsRepository?.getPrivateSharingSync() ?: false

    if (!isPrivate) {
        launchShareIntent(context, uris, mimeType)
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        val preparedUris = scrubAndPrepareUris(context, uris)
        withContext(Dispatchers.Main) {
            launchShareIntent(context, preparedUris, mimeType)
        }
    }
}

private fun scrubAndPrepareUris(context: Context, uris: List<Uri>): List<Uri> {
    val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
    val cutoff = System.currentTimeMillis() - 30 * 60 * 1000L
    shareDir.listFiles()?.forEach { file ->
        if (file.lastModified() < cutoff) file.delete()
    }

    return uris.map { uri ->
        val mime = context.contentResolver.getType(uri).orEmpty()
        val isImage = mime.startsWith("image/") || (!mime.startsWith("video/") && !uri.toString().endsWith(".mp4", ignoreCase = true))
        if (!isImage) {
            return@map uri
        }

        runCatching {
            val tempFile = File.createTempFile("share_", ".jpg", shareDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@map uri

            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_WRITE)?.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                val tagsToStrip = listOf(
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_ALTITUDE,
                    ExifInterface.TAG_GPS_ALTITUDE_REF,
                    ExifInterface.TAG_GPS_SPEED,
                    ExifInterface.TAG_GPS_SPEED_REF,
                    ExifInterface.TAG_GPS_IMG_DIRECTION,
                    ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_ARTIST,
                    ExifInterface.TAG_USER_COMMENT,
                )
                for (tag in tagsToStrip) {
                    exif.setAttribute(tag, null)
                }
                exif.saveAttributes()
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
        }.getOrDefault(uri)
    }
}

private fun launchShareIntent(context: Context, uris: List<Uri>, mimeType: String? = null) {
    if (uris.isEmpty()) return
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris.first()) }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }
    send.type = mimeType?.ifEmpty { "*/*" } ?: "*/*"
    send.clipData = ClipData.newRawUri(null, uris.first()).also { clip ->
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
    }
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(send, null).apply {
        if (context !is android.app.Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    context.startActivity(chooser)
}
