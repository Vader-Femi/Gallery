package com.femi.gallery.data.label

import android.content.Context
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.label.DetectedLabel
import com.femi.gallery.domain.label.LabelCodec
import com.femi.gallery.domain.repository.LabelStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileLabelStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : LabelStore {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    override suspend fun load(): List<DetectedLabel> = withContext(io) {
        val target = file
        if (!target.exists()) emptyList() else LabelCodec.decode(target.readBytes()).orEmpty()
    }

    override suspend fun save(entries: List<DetectedLabel>) = withContext(io) {
        val target = file
        val temporary = File(target.parentFile, "$FILE_NAME.tmp")
        temporary.writeBytes(LabelCodec.encode(entries))
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        Unit
    }

    private companion object {
        const val FILE_NAME = "labels.bin"
    }
}
