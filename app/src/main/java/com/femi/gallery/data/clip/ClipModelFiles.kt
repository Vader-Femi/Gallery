package com.femi.gallery.data.clip

import android.content.Context
import android.net.Uri
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.clip.ClipModelKind
import com.femi.gallery.domain.clip.ClipModelSignature
import com.femi.gallery.domain.clip.ClipModelStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * the phone. On the very first run the two models bundled in the assets folder are copied in; after that they only
 * come back when the person asks for them.
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

    private val _isCopying = MutableStateFlow(false)
    val isCopying: StateFlow<Boolean> = _isCopying.asStateFlow()

    /** One change to the files at a time: the first-run copy, a restore, an import and a removal never overlap. */
    private val changes = Mutex()

    init {
        CoroutineScope(SupervisorJob() + io).launch {
            _isCopying.value = true
            try {
                changes.withLock { installBundledOnFirstRun() }
            } finally {
                _isCopying.value = false
            }
        }
    }

    /**
     * The first time only: when no model is there at all, copies the two bundled ones in and leaves a marker. After
     * that the bundled models are never copied again unless the person asks ([restoreBundled]), so "Remove models"
     * stays removed. A copy that failed leaves no marker, so it is tried again at the next start.
     */
    private suspend fun installBundledOnFirstRun() {
        val marker = File(directory, BUNDLED_MARKER)
        if (marker.exists()) return
        directory.mkdirs()
        directory.listFiles { f -> f.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() } // left by a copy that was cut short
        val noneYet = ClipModelKind.entries.none { file(it).length() > 0 }
        if (noneYet && !copyBundled()) return
        // Models the person had installed before the bundled ones existed are kept as they are.
        marker.createNewFile()
        refresh()
    }

    /** Replaces both models with the ones bundled in the app. False if either copy failed (nothing is left half-written). */
    suspend fun restoreBundled(): Boolean = withContext(io) {
        _isCopying.value = true
        try {
            changes.withLock {
                directory.mkdirs()
                val copied = copyBundled()
                if (copied) File(directory, BUNDLED_MARKER).createNewFile()
                refresh()
                copied
            }
        } finally {
            _isCopying.value = false
        }
    }

    private suspend fun copyBundled(): Boolean =
        copyAsset(BUNDLED_IMAGE, file(ClipModelKind.Image)) && copyAsset(BUNDLED_TEXT, file(ClipModelKind.Text))

    /** Copies a bundled file through a temporary one, and only keeps it when it is as long as the original. */
    private suspend fun copyAsset(assetPath: String, target: File): Boolean {
        val temporary = File(target.parentFile, target.name + PART_SUFFIX)
        return try {
            val expected = runCatching { context.assets.openFd(assetPath).use { it.length } }.getOrDefault(-1L)
            context.assets.open(assetPath).use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(COPY_BUFFER)
                    while (true) {
                        currentCoroutineContext().ensureActive() // lets a cancelled copy stop between pieces
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                    }
                }
            }
            val ok = temporary.length() > 0 && (expected < 0 || temporary.length() == expected)
            if (!ok) {
                Log.w(TAG, "Bundled $assetPath copied to ${temporary.length()} bytes, expected $expected")
            } else if (!temporary.renameTo(target)) {
                target.delete()
                if (!temporary.renameTo(target)) Log.w(TAG, "Cannot move the copy of $assetPath into place")
            }
            if (temporary.exists()) temporary.delete()
            target.length() > 0 && (expected < 0 || target.length() == expected)
        } catch (e: CancellationException) {
            temporary.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cannot copy the bundled $assetPath", e)
            temporary.delete()
            false
        }
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
    suspend fun import(kind: ClipModelKind, source: Uri): Boolean = withContext(io) { changes.withLock {
        val target = file(kind)
        val temporary = File(target.parentFile, fileName(kind) + PART_SUFFIX)
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
    } }

    suspend fun delete(kind: ClipModelKind) = withContext(io) {
        changes.withLock {
            file(kind).delete()
            refresh()
        }
    }

    /** Removes both models. The bundled ones are not copied back until the person asks. */
    suspend fun deleteAll() = withContext(io) {
        changes.withLock {
            ClipModelKind.entries.forEach { file(it).delete() }
            File(directory, BUNDLED_MARKER).createNewFile()
            refresh()
        }
    }

    private fun refresh() {
        _status.value = readStatus()
    }

    private fun readStatus(): ClipModelStatus =
        ClipModelStatus.of(file(ClipModelKind.Image).length() > 0, file(ClipModelKind.Text).length() > 0)

    private companion object {
        const val TAG = "ClipModel"
        const val COPY_BUFFER = 256 * 1024
        const val PART_SUFFIX = ".part"
        const val BUNDLED_MARKER = ".bundled_done"
        const val BUNDLED_IMAGE = "onnx/vision_model_quantized.onnx"
        const val BUNDLED_TEXT = "onnx/text_model_quantized.onnx"
    }
}
