package com.femi.gallery.data.color

import android.content.ContentUris
import android.content.Context
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.color.ColorClassifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun scan(id: Long): Set<String> = withContext(io) {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        try {
            val signal = CancellationSignal()
            val bitmap = context.contentResolver.loadThumbnail(uri, Size(64, 64), signal)
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            ColorClassifier.classify(pixels, width, height)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read thumbnail/colours for photo $id", e)
            emptySet()
        }
    }

    private companion object {
        const val TAG = "ColorScan"
    }
}
