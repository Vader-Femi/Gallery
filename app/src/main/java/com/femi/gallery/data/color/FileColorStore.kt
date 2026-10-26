package com.femi.gallery.data.color

import android.content.Context
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.color.ColorCodec
import com.femi.gallery.domain.color.DetectedColor
import com.femi.gallery.domain.repository.ColorStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileColorStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ColorStore {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    override suspend fun load(): List<DetectedColor> = withContext(io) {
        val target = file
        if (!target.exists()) emptyList() else ColorCodec.decode(target.readBytes()).orEmpty()
    }

    override suspend fun save(entries: List<DetectedColor>) = withContext(io) {
        val target = file
        val temporary = File(target.parentFile, "$FILE_NAME.tmp")
        temporary.writeBytes(ColorCodec.encode(entries))
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        Unit
    }

    private companion object {
        const val FILE_NAME = "colors.bin"
    }
}
