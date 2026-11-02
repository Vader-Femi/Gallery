package com.femi.gallery.data.video

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.model.AlbumPaths
import com.femi.gallery.domain.video.TrimEvent
import com.femi.gallery.domain.video.TrimFailure
import com.femi.gallery.domain.video.TrimMath
import com.femi.gallery.domain.video.TrimRange
import com.femi.gallery.domain.video.VideoTrimRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes the trimmed copy with Media3 Transformer: the clip is exported to a file in the cache and then added to the
 * library (MediaStore) in the original's folder, with the original's date taken. The original is never touched.
 *
 * Transformer must be used from one thread with a Looper, so everything that touches it runs on the main thread.
 * Not verified on a device when this was written; the `VideoTrim` log tag tells what happened.
 */
@Singleton
@OptIn(UnstableApi::class)
class TransformerVideoTrimRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : VideoTrimRepository {

    private class Source(
        val name: String,
        val relativePath: String,
        val dateTaken: Long,
        val sizeBytes: Long,
        val durationMs: Long,
    )

    override fun trim(uri: String, range: TrimRange): Flow<TrimEvent> = callbackFlow {
        val source = withContext(io) { readSource(Uri.parse(uri)) }
        if (source == null) {
            Log.w(TAG, "Cannot read the details of $uri")
            trySend(TrimEvent.Failed(TrimFailure.SourceUnreadable))
            close()
            return@callbackFlow
        }

        // The clip is written to the cache first and then copied into the library, so room for both is needed.
        val cacheDir = File(context.cacheDir, "trim").apply { mkdirs() }
        val temp = File(cacheDir, "trim_${System.nanoTime()}.mp4")
        val estimate = if (source.durationMs > 0) source.sizeBytes * range.lengthMs / source.durationMs else source.sizeBytes
        if (cacheDir.usableSpace < estimate * 2 + SPACE_MARGIN_BYTES) {
            Log.w(TAG, "Not enough space: ${cacheDir.usableSpace} free, about $estimate needed twice")
            trySend(TrimEvent.Failed(TrimFailure.NotEnoughSpace))
            close()
            return@callbackFlow
        }

        var transformer: Transformer? = null
        var finished = false
        val startedAt = System.currentTimeMillis()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                Log.i(
                    TAG,
                    "Export finished in ${System.currentTimeMillis() - startedAt} ms: ${exportResult.fileSizeBytes} bytes, " +
                        "optimizationResult=${exportResult.optimizationResult}, " +
                        "video=${exportResult.videoEncoderName ?: "copied"}, audio=${exportResult.audioEncoderName ?: "copied"}",
                )
                finished = true
                launch(io) {
                    val added = try {
                        addToLibrary(temp, source, uri)
                    } catch (e: Exception) {
                        Log.e(TAG, "Cannot add the trimmed video to the library", e)
                        null
                    }
                    temp.delete()
                    trySend(added ?: TrimEvent.Failed(TrimFailure.Other))
                    close()
                }
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                Log.e(TAG, "Export failed: ${exportException.errorCodeName} (${exportException.errorCode})", exportException)
                finished = true
                temp.delete()
                trySend(TrimEvent.Failed(failureOf(exportException.errorCode)))
                close()
            }
        }

        launch(Dispatchers.Main.immediate) {
            try {
                val clip = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(range.startMs)
                    .setEndPositionMs(range.endMs)
                    .build()
                val item = MediaItem.Builder().setUri(Uri.parse(uri)).setClippingConfiguration(clip).build()
                val built = Transformer.Builder(context)
                    .setLooper(Looper.getMainLooper())
                    // Copies what it can between the key frame before the start and the end, and re-encodes only the
                    // first stretch; falls back to a full export by itself when the pieces cannot be joined.
                    .experimentalSetTrimOptimizationEnabled(true)
                    .addListener(listener)
                    .build()
                transformer = built
                Log.i(TAG, "Trimming $uri from ${range.startMs} ms to ${range.endMs} ms")
                built.start(EditedMediaItem.Builder(item).build(), temp.absolutePath)

                val holder = ProgressHolder()
                while (isActive && !finished) {
                    delay(PROGRESS_INTERVAL_MS)
                    if (built.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        trySend(TrimEvent.Progress(holder.progress.coerceIn(0, 100)))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot start the export", e)
                temp.delete()
                trySend(TrimEvent.Failed(TrimFailure.Other))
                close()
            }
        }

        awaitClose {
            // The person left, or the flow ended: stop a running export and remove what it left behind.
            if (!finished) {
                launch(Dispatchers.Main.immediate) { transformer?.cancel() }
                Log.i(TAG, "Export cancelled")
            }
            temp.delete()
        }
    }

    override suspend fun keyFrames(uri: String): LongArray = withContext(io) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return@withContext LongArray(0)
            extractor.selectTrack(track)
            val times = ArrayList<Long>()
            while (extractor.sampleTime >= 0 && times.size < MAX_KEY_FRAMES) {
                if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) times.add(extractor.sampleTime / 1000L)
                if (!extractor.advance()) break
            }
            times.toLongArray().also { Log.i(TAG, "${it.size} key frames found in $uri") }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read the key frames of $uri", e)
            LongArray(0)
        } finally {
            extractor.release()
        }
    }

    private fun readSource(uri: Uri): Source? {
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DURATION,
        )
        return context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            Source(
                name = c.getString(0).orEmpty(),
                relativePath = c.getString(1).orEmpty(),
                dateTaken = c.getLong(2),
                sizeBytes = c.getLong(3),
                durationMs = c.getLong(4),
            )
        }
    }

    /** Adds the exported file to the library next to the original and returns the event for it, or null on failure. */
    private fun addToLibrary(file: File, source: Source, originalUri: String): TrimEvent.Done? {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val folder = AlbumPaths.forMedia(source.relativePath.ifBlank { "Movies/" }, isVideo = true)

        val taken = HashSet<String>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(folder),
            null,
        )?.use { while (it.moveToNext()) it.getString(0)?.let(taken::add) }
        val name = TrimMath.outputName(source.name, taken)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            if (source.dateTaken > 0) put(MediaStore.MediaColumns.DATE_TAKEN, source.dateTaken)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(target)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("Cannot write $target")
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            logWhatWasKept(Uri.parse(originalUri), target)
            TrimEvent.Done(target.toString(), name)
        } catch (e: Exception) {
            resolver.delete(target, null, null)
            throw e
        }
    }

    /** Whether the place the video was filmed at survived the export; Transformer's handling of it is unverified. */
    private fun logWhatWasKept(original: Uri, copy: Uri) {
        fun location(uri: Uri): String? = runCatching {
            MediaMetadataRetriever().use { r ->
                r.setDataSource(context, uri)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            }
        }.getOrNull()
        Log.i(TAG, "Location in the original: ${location(original) != null}, in the copy: ${location(copy) != null}")
    }

    private fun failureOf(code: Int): TrimFailure = when (code) {
        ExportException.ERROR_CODE_IO_FILE_NOT_FOUND, ExportException.ERROR_CODE_IO_NO_PERMISSION -> TrimFailure.SourceUnreadable
        in ExportException.ERROR_CODE_DECODER_INIT_FAILED..ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED -> TrimFailure.Unsupported
        else -> TrimFailure.Other
    }

    private companion object {
        const val TAG = "VideoTrim"
        const val PROGRESS_INTERVAL_MS = 250L
        const val SPACE_MARGIN_BYTES = 50L * 1024 * 1024
        const val MAX_KEY_FRAMES = 200_000
    }
}
