package com.femi.gallery.presentation.common

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.femi.gallery.R
import com.femi.gallery.di.ApplicationScope
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

    @get:ApplicationScope
    val applicationScope: CoroutineScope
}

/**
 * Shares one or more photos/videos through the system share sheet. With private sharing on, every photo is first
 * copied without its metadata ([PhotoScrubber]); if any copy cannot be made, nothing is shared (it never falls back to
 * the original, which would hand out the place the photo was taken). Videos are shared as they are.
 */
fun shareUris(context: Context, uris: List<Uri>, mimeType: String? = null) {
    if (uris.isEmpty()) return
    val app = context.applicationContext

    val entryPoint = try {
        EntryPointAccessors.fromApplication(app, ShareEntryPoint::class.java)
    } catch (e: Exception) {
        // The setting cannot be read: sharing something other than what the person chose is worse than not sharing.
        Toast.makeText(app, R.string.private_share_failed, Toast.LENGTH_LONG).show()
        return
    }

    if (!entryPoint.settingsRepository.getPrivateSharingSync()) {
        launchShareIntent(context, uris, mimeType)
        return
    }

    // Not tied to the screen: the copies are made even if the person leaves it meanwhile.
    entryPoint.applicationScope.launch(Dispatchers.IO) {
        val shareDir = File(app.cacheDir, SHARE_DIR).apply { mkdirs() }
        deleteOldCopies(shareDir)
        val made = ArrayList<File>()
        val prepared = try {
            uris.map { uri ->
                val mime = app.contentResolver.getType(uri).orEmpty()
                if (mime.startsWith("video/")) {
                    uri
                } else {
                    val copy = PhotoScrubber.scrub(app, uri, shareDir).also(made::add)
                    FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", copy)
                }
            }
        } catch (e: Exception) {
            Log.w("ShareMedia", "Private sharing: cannot remove the metadata, nothing is shared", e)
            made.forEach { it.delete() }
            withContext(Dispatchers.Main) { Toast.makeText(app, R.string.private_share_failed, Toast.LENGTH_LONG).show() }
            return@launch
        }
        val types = prepared.map { app.contentResolver.getType(it).orEmpty() }.distinct()
        val sendType = types.singleOrNull()?.ifEmpty { null } ?: if (types.all { it.startsWith("image/") }) "image/*" else "*/*"
        withContext(Dispatchers.Main) {
            // The screen may be gone by now.
            val host = (context as? Activity)?.takeUnless { it.isFinishing || it.isDestroyed } ?: app
            launchShareIntent(host, prepared, sendType)
        }
    }
}

private const val SHARE_DIR = "share"
private const val COPY_LIFETIME_MS = 30 * 60 * 1000L

/** The copies made for sharing are removed after a while (and all of them when the app starts, see [clearShareCopies]). */
private fun deleteOldCopies(shareDir: File) {
    val cutoff = System.currentTimeMillis() - COPY_LIFETIME_MS
    shareDir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
}

/** Deletes every copy that private sharing made earlier; they are only needed while the share sheet is in use. */
fun clearShareCopies(context: Context) {
    File(context.cacheDir, SHARE_DIR).deleteRecursively()
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
