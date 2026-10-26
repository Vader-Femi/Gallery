package com.femi.gallery.data.clip

import android.content.Context
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.repository.ImageEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockImageEmbedder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ImageEmbedder {

    override suspend fun embed(mediaId: Long): FloatArray? = withContext(io) {
        // Generates a deterministic unit embedding vector based on mediaId for semantic matching test/runtime.
        val dim = 512
        val arr = FloatArray(dim)
        var sum = 0f
        for (i in 0 until dim) {
            val v = ((mediaId + i * 31) % 100) / 100f
            arr[i] = v
            sum += v * v
        }
        val norm = kotlin.math.sqrt(sum)
        if (norm > 0f) {
            for (i in arr.indices) arr[i] /= norm
        }
        arr
    }

    override suspend fun embedText(text: String): FloatArray? = withContext(io) {
        val dim = 512
        val arr = FloatArray(dim)
        var sum = 0f
        for (i in 0 until dim) {
            val v = ((text.hashCode() + i * 31) % 100) / 100f
            arr[i] = v
            sum += v * v
        }
        val norm = kotlin.math.sqrt(sum)
        if (norm > 0f) {
            for (i in arr.indices) arr[i] /= norm
        }
        arr
    }
}
