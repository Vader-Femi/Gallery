package com.femi.gallery.data.clip

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.util.Log
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.clip.ClipModelIo
import com.femi.gallery.domain.clip.ClipModelKind
import com.femi.gallery.domain.clip.ClipPreprocess
import com.femi.gallery.domain.clip.ClipQuantizer
import com.femi.gallery.domain.clip.ClipTokenizer
import com.femi.gallery.domain.clip.ModelPort
import com.femi.gallery.domain.repository.ImageEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns photos and descriptions into CLIP embeddings with two ONNX models run by ONNX Runtime, on the phone.
 * The models are the files the person installed (see [ClipModelFiles]); until both are there, or if they turn
 * out to be unusable, [isAvailable] is false and nothing else in the app is affected.
 *
 * What the models call their inputs and outputs is read from the models themselves ([ClipModelIo]), so files
 * from different exporters work. Each model is opened the first time it is needed and closed when the files change.
 */
@Singleton
class OnnxClipEmbedder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    private val files: ClipModelFiles,
) : ImageEmbedder {

    /** What a model reported and was checked for, or why it cannot be used. */
    sealed interface Check {
        data class Ok(val dimension: Int) : Check
        data class Unusable(val reason: String) : Check
    }

    private class ImageModel(val session: OrtSession, val inputName: String, val size: Int, val outputIndex: Int)

    private class TextModel(
        val session: OrtSession,
        val idsName: String,
        val idsIsLong: Boolean,
        val maskName: String?,
        val maskIsLong: Boolean,
        val length: Int,
        val outputIndex: Int,
    )

    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val lock = Mutex()

    private var openedFor = 0L
    private var image: ImageModel? = null
    private var text: TextModel? = null
    private var tokenizer: ClipTokenizer? = null

    @Volatile
    private var brokenFor = 0L

    override val signature: Long get() = if (isAvailable) files.signature else 0L

    override val isAvailable: Boolean
        get() {
            val signature = files.signature
            return files.status.value.isComplete && signature != brokenFor
        }

    override suspend fun embed(mediaId: Long): FloatArray? = withContext(io) {
        try {
            val model = imageModel() ?: return@withContext null
            val pixels = loadPixels(mediaId, model.size) ?: return@withContext null
            val data = ClipPreprocess.toTensor(pixels, model.size)
            val shape = longArrayOf(1, 3, model.size.toLong(), model.size.toLong())
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(data), shape).use { input ->
                model.session.run(mapOf(model.inputName to input)).use { result ->
                    ClipQuantizer.normalize(vectorOf(result.get(model.outputIndex).value))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not embed photo $mediaId", e)
            null
        }
    }

    override suspend fun embedText(text: String): FloatArray? = withContext(io) {
        try {
            val model = textModel() ?: return@withContext null
            val words = tokenizer()
            val ids = words.tokenize(text, model.length)
            val shape = longArrayOf(1, model.length.toLong())
            val endIndex = ids.indexOfFirst { it == ClipTokenizer.END_ID }.coerceAtLeast(0)
            val inputs = HashMap<String, OnnxTensor>()
            try {
                inputs[model.idsName] = wholeNumbers(ids, model.idsIsLong, shape)
                model.maskName?.let { name ->
                    inputs[name] = wholeNumbers(IntArray(ids.size) { if (it <= endIndex) 1 else 0 }, model.maskIsLong, shape)
                }
                model.session.run(inputs).use { result ->
                    ClipQuantizer.normalize(vectorOf(result.get(model.outputIndex).value))
                }
            } finally {
                inputs.values.forEach { it.close() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not embed the description", e)
            null
        }
    }

    /**
     * Opens the [kind] model and runs it once on a blank input, to find out whether it can be used and how long its
     * embeddings are. Used when a file is installed, so the person is told at once instead of at the first search.
     */
    suspend fun check(kind: ClipModelKind, file: java.io.File = files.file(kind)): Check = withContext(io) {
        try {
            lock.withLock {
                closeLocked()
                when (kind) {
                    ClipModelKind.Image -> {
                        val model = openImage(file) ?: return@withLock Check.Unusable("not a picture model")
                        val size = model.size
                        val blank = FloatArray(3 * size * size)
                        OnnxTensor.createTensor(environment, FloatBuffer.wrap(blank), longArrayOf(1, 3, size.toLong(), size.toLong())).use { input ->
                            model.session.run(mapOf(model.inputName to input)).use { result ->
                                Check.Ok(vectorOf(result.get(model.outputIndex).value).size)
                            }
                        }
                    }
                    ClipModelKind.Text -> {
                        val model = openText(file) ?: return@withLock Check.Unusable("not a text model")
                        // "start" and "end" and nothing else: the shortest description there is.
                        val ids = IntArray(model.length)
                        ids[0] = ClipTokenizer.START_ID
                        if (model.length > 1) ids[1] = ClipTokenizer.END_ID
                        val shape = longArrayOf(1, model.length.toLong())
                        val inputs = HashMap<String, OnnxTensor>()
                        try {
                            inputs[model.idsName] = wholeNumbers(ids, model.idsIsLong, shape)
                            model.maskName?.let { inputs[it] = wholeNumbers(IntArray(model.length) { i -> if (i <= 1) 1 else 0 }, model.maskIsLong, shape) }
                            model.session.run(inputs).use { result -> Check.Ok(vectorOf(result.get(model.outputIndex).value).size) }
                        } finally {
                            inputs.values.forEach { it.close() }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "The $kind model cannot be used", e)
            Check.Unusable(e.message ?: "unreadable")
        } finally {
            lock.withLock { closeLocked() }
        }
    }

    /** Frees both models (they take a good part of 100 MB); they are opened again when next needed. */
    suspend fun release() = lock.withLock { closeLocked() }

    private suspend fun imageModel(): ImageModel? = lock.withLock {
        reopenIfFilesChanged()
        image ?: openOrNull("picture") { openImage() }?.also { image = it }
    }

    private suspend fun textModel(): TextModel? = lock.withLock {
        reopenIfFilesChanged()
        text ?: openOrNull("text") { openText() }?.also { text = it }
    }

    /** Opens a model; one that cannot be opened makes the embedder unavailable until the files change, instead of failing on every photo. */
    private fun <T> openOrNull(what: String, open: () -> T?): T? {
        val opened = try {
            open()
        } catch (e: Exception) {
            Log.w(TAG, "The $what model cannot be opened", e)
            null
        }
        if (opened == null) brokenFor = files.signature
        return opened
    }

    private fun tokenizer(): ClipTokenizer = tokenizer ?: synchronized(this) {
        tokenizer ?: ClipTokenizer(readVocabulary()).also { tokenizer = it }
    }

    private fun readVocabulary(): String =
        context.assets.open(VOCABULARY_ASSET).use { it.bufferedReader().readText() }

    private fun reopenIfFilesChanged() {
        val now = files.signature
        if (now != openedFor) {
            closeLocked()
            openedFor = now
        }
    }

    private fun closeLocked() {
        image?.session?.close()
        text?.session?.close()
        image = null
        text = null
    }

    private fun options() = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(THREADS)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }

    private fun openImage(file: java.io.File = files.file(ClipModelKind.Image)): ImageModel? {
        val session = environment.createSession(file.absolutePath, options())
        val input = ClipModelIo.imageInput(ports(session.inputInfo, session.inputNames)) ?: run { session.close(); return null }
        val outputs = ports(session.outputInfo, session.outputNames)
        val output = ClipModelIo.embeddingOutput(outputs) ?: run { session.close(); return null }
        return ImageModel(session, input.name, ClipModelIo.imageSize(input), session.outputNames.indexOf(output.name))
    }

    private fun openText(file: java.io.File = files.file(ClipModelKind.Text)): TextModel? {
        val session = environment.createSession(file.absolutePath, options())
        val inputs = ports(session.inputInfo, session.inputNames)
        val ids = ClipModelIo.textIds(inputs) ?: run { session.close(); return null }
        val mask = ClipModelIo.textMask(inputs)
        val output = ClipModelIo.embeddingOutput(ports(session.outputInfo, session.outputNames)) ?: run { session.close(); return null }
        return TextModel(
            session = session,
            idsName = ids.name,
            idsIsLong = ids.type == ModelPort.ElementType.Int64,
            maskName = mask?.name,
            maskIsLong = mask?.type == ModelPort.ElementType.Int64,
            length = ClipModelIo.contextLength(ids),
            outputIndex = session.outputNames.indexOf(output.name),
        )
    }

    /** What the model says about each of its [names], in the model's own order. */
    private fun ports(info: Map<String, ai.onnxruntime.NodeInfo>, names: Set<String>): List<ModelPort> =
        names.mapNotNull { name ->
            val tensor = info[name]?.info as? TensorInfo ?: return@mapNotNull null
            ModelPort(
                name = name,
                shape = tensor.shape,
                type = when (tensor.type) {
                    ai.onnxruntime.OnnxJavaType.FLOAT -> ModelPort.ElementType.Float
                    ai.onnxruntime.OnnxJavaType.INT32 -> ModelPort.ElementType.Int32
                    ai.onnxruntime.OnnxJavaType.INT64 -> ModelPort.ElementType.Int64
                    else -> ModelPort.ElementType.Other
                },
            )
        }

    private fun wholeNumbers(values: IntArray, asLong: Boolean, shape: LongArray): OnnxTensor =
        if (asLong) {
            OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(values.size) { values[it].toLong() }), shape)
        } else {
            OnnxTensor.createTensor(environment, IntBuffer.wrap(values), shape)
        }

    /** The embedding in a model's output, which is a batch of one: [[...]] or a plain [...]. */
    private fun vectorOf(value: Any?): FloatArray = when (value) {
        is FloatArray -> value
        is Array<*> -> value.firstOrNull() as? FloatArray ?: throw IllegalStateException("The output is not a vector")
        else -> throw IllegalStateException("The output is not a vector")
    }

    /** The photo or video keyframe as the model wants it: scaled until its shorter side fits, cropped in the middle, as packed pixels. */
    private fun loadPixels(mediaId: Long, size: Int): IntArray? {
        val imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
        val decoded: Bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, imageUri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val (w, h) = ClipPreprocess.scaledSize(info.size.width, info.size.height, size)
                decoder.setTargetSize(w, h)
            }
        } catch (_: Exception) {
            // Not a readable image; try extracting a video keyframe from the middle of the video.
            loadVideoKeyframe(mediaId, size)
        } ?: return null

        val scaled = if (decoded.width < size || decoded.height < size) {
            Bitmap.createScaledBitmap(decoded, maxOf(size, decoded.width), maxOf(size, decoded.height), true)
        } else {
            decoded
        }
        val (left, top) = ClipPreprocess.cropOrigin(scaled.width, scaled.height, size)
        val square = Bitmap.createBitmap(scaled, left, top, size, size)
        val pixels = IntArray(size * size)
        square.getPixels(pixels, 0, size, 0, 0, size, size)
        if (square !== scaled) square.recycle()
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return pixels
    }

    /** Extracts a keyframe from the middle of the video with [mediaId] using MediaMetadataRetriever. */
    private fun loadVideoKeyframe(mediaId: Long, size: Int): Bitmap? {
        val videoUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val targetTimeUs = if (durationMs > 0) (durationMs / 2) * 1000L else 0L
            val raw = retriever.getScaledFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, size, size)
                ?: retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val (w, h) = ClipPreprocess.scaledSize(raw.width, raw.height, size)
            if (raw.width != w || raw.height != h) {
                val scaled = Bitmap.createScaledBitmap(raw, w, h, true)
                if (scaled !== raw) raw.recycle()
                scaled
            } else {
                raw
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private companion object {
        const val TAG = "ClipModel"
        const val VOCABULARY_ASSET = "clip/bpe_simple_vocab_16e6.txt"
        const val THREADS = 2
    }
}
