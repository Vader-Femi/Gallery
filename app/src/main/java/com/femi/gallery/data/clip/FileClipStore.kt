package com.femi.gallery.data.clip

import android.content.Context
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.clip.ClipCodec
import com.femi.gallery.domain.clip.DetectedEmbedding
import com.femi.gallery.domain.repository.ClipStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileClipStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ClipStore {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    override suspend fun load(): List<DetectedEmbedding> = withContext(io) {
        val target = file
        if (!target.exists()) emptyList() else ClipCodec.decode(target.readBytes()).orEmpty()
    }

    override suspend fun save(entries: List<DetectedEmbedding>) = withContext(io) {
        val target = file
        val temporary = File(target.parentFile, "$FILE_NAME.tmp")
        temporary.writeBytes(ClipCodec.encode(entries))
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        Unit
    }

    private companion object {
        const val FILE_NAME = "clip.bin"
    }
}
