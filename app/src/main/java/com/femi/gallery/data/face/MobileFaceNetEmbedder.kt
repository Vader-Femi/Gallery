package com.femi.gallery.data.face

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.femi.gallery.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class MobileFaceNetEmbedder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val lock = Mutex()
    private var session: OrtSession? = null
    private var inputName: String = "data"
    private var outputName: String = "output"

    suspend fun embed(alignedFace112: Bitmap): FloatArray? = withContext(io) {
        lock.withLock {
            try {
                val sess = getSession() ?: return@withContext null
                val width = alignedFace112.width
                val height = alignedFace112.height
                val pixels = IntArray(width * height)
                alignedFace112.getPixels(pixels, 0, width, 0, 0, width, height)

                // NCHW tensor: [1, 3, 112, 112]
                val floatData = FloatArray(3 * width * height)
                val channelSize = width * height
                for (i in 0 until channelSize) {
                    val pixel = pixels[i]
                    val r = ((pixel shr 16) and 0xFF).toFloat()
                    val g = ((pixel shr 8) and 0xFF).toFloat()
                    val b = (pixel and 0xFF).toFloat()

                    // InsightFace ArcFace normalization: (x - 127.5) / 128.0
                    floatData[i] = (r - 127.5f) / 128f
                    floatData[channelSize + i] = (g - 127.5f) / 128f
                    floatData[channelSize * 2 + i] = (b - 127.5f) / 128f
                }

                val shape = longArrayOf(1, 3, height.toLong(), width.toLong())
                val rawOutput = OnnxTensor.createTensor(environment, FloatBuffer.wrap(floatData), shape).use { tensor ->
                    sess.run(mapOf(inputName to tensor)).use { result ->
                        result.get(0).value
                    }
                }

                val vector = extractVector(rawOutput) ?: return@withContext null

                // L2 normalize embedding vector
                var normSq = 0f
                for (v in vector) normSq += v * v
                val norm = sqrt(normSq)
                if (norm > 1e-6f) {
                    for (i in vector.indices) vector[i] /= norm
                }
                vector
            } catch (e: Exception) {
                Log.w(TAG, "Failed to run MobileFaceNet embedding", e)
                null
            }
        }
    }

    private fun extractVector(value: Any?): FloatArray? {
        return when (value) {
            is Array<*> -> {
                when (val first = value.firstOrNull()) {
                    is FloatArray -> first
                    is Array<*> -> (first.firstOrNull() as? FloatArray)
                    else -> null
                }
            }
            is FloatArray -> value
            else -> null
        }
    }

    private fun getSession(): OrtSession? {
        session?.let { return it }
        val modelFile = ensureModelFile() ?: return null
        return try {
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val sess = environment.createSession(modelFile.absolutePath, opts)
            inputName = sess.inputNames.firstOrNull() ?: "data"
            outputName = sess.outputNames.firstOrNull() ?: "output"
            session = sess
            sess
        } catch (e: Exception) {
            Log.e(TAG, "Cannot create MobileFaceNet ONNX session", e)
            null
        }
    }

    private fun ensureModelFile(): File? {
        val target = File(context.filesDir, "models/face/mobilefacenet.onnx")
        if (target.exists() && target.length() > 100_000) return target
        target.parentFile?.mkdirs()
        return try {
            context.assets.open("onnx/mobilefacenet.onnx").use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            target
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy mobilefacenet.onnx from assets", e)
            null
        }
    }

    companion object {
        private const val TAG = "MobileFaceNet"
    }
}
