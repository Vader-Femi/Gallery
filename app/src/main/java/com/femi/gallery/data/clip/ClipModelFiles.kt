package com.femi.gallery.data.clip

import android.content.Context
import android.net.Uri
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.clip.ClipModelKind
import com.femi.gallery.domain.clip.ClipModelSignature
import com.femi.gallery.domain.clip.ClipModelStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The two model files (one that reads pictures, one that reads text) in the app's private storage. The app has
 * no internet access, so the person copies them in once, from wherever they downloaded them, and they never leave
 * the phone. By default, it will attempt to copy models bundled in the assets folder on first run.
 */
@Singleton
class ClipModelFiles @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private fun fileName(kind: ClipModelKind) = when (kind) {
        ClipModelKind.Image -> "clip_image.onnx"
        ClipModelKind.Text -> "clip_text.onnx"
    }

    private val directory: File get() = File(context.filesDir, "models/clip")

    fun file(kind: ClipModelKind): File = File(directory, fileName(kind))

    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<ClipModelStatus> = _status.asStateFlow()

    init {
        CoroutineScope(io).launch {
            ensureDefaultModels()
        }
    }

    private fun ensureDefaultModels() {
        var changed = false
        if (!file(ClipModelKind.Image).exists()) {
            changed = copyAsset("onnx/vision_model_quantized.onnx", file(ClipModelKind.Image)) || changed
        }
        if (!file(ClipModelKind.Text).exists()) {
            changed = copyAsset("onnx/text_model_quantized.onnx", file(ClipModelKind.Text)) || changed
        }
        if (changed) refresh()
    }

    private fun copyAsset(assetPath: String, target: File): Boolean {
        try {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                val temporary = File(target.parentFile, "${target.name}.part")
                temporary.outputStream().use { output ->
                    input.copyTo(output, COPY_BUFFER)
                }
                if (temporary.length() > 0) {
                    if (temporary.renameTo(target)) return true
                    target.delete()
                    if (temporary.renameTo(target)) return true
                } else {
                    temporary.delete()
                }
            }
        } catch (e: Exception) {
            // Asset might not exist or IO error
        }
        return false
    }

    /** A number that changes when either file changes; 0 until both are installed. */
    val signature: Long
        get() {
            if (!_status.value.isComplete) return 0L
            val image = file(ClipModelKind.Image)
            val text = file(ClipModelKind.Text)
            return ClipModelSignature.of(image.length(), image.lastModified(), text.length(), text.lastModified())
        }

    /** Copies the file at [source] in as the [kind] model. False if it could not be read or written. */
    suspend fun import(kind: ClipModelKind, source: Uri): Boolean = withContext(io) {
        val target = file(kind)
        val temporary = File(target.parentFile, "${fileName(kind)}.part")
        try {
            target.parentFile?.mkdirs()
            val input = context.contentResolver.openInputStream(source) ?: return@withContext false
            input.use { from -> temporary.outputStream().use { to -> from.copyTo(to, COPY_BUFFER) } }
            if (temporary.length() == 0L) {
                temporary.delete()
                return@withContext false
            }
            if (!temporary.renameTo(target)) {
                target.delete()
                if (!temporary.renameTo(target)) return@withContext false
            }
            refresh()
            true
        } catch (e: IOException) {
            temporary.delete()
            false
        } catch (e: SecurityException) {
            temporary.delete()
            false
        }
    }

    suspend fun delete(kind: ClipModelKind) = withContext(io) {
        file(kind).delete()
        refresh()
    }

    suspend fun deleteAll() = withContext(io) {
        ClipModelKind.entries.forEach { file(it).delete() }
        refresh()
    }

    private fun refresh() {
        _status.value = readStatus()
    }

    private fun readStatus(): ClipModelStatus =
        ClipModelStatus.of(file(ClipModelKind.Image).length() > 0, file(ClipModelKind.Text).length() > 0)

    private companion object {
        const val COPY_BUFFER = 256 * 1024
    }
}
