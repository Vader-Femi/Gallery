package com.femi.gallery.data.text

import android.content.Context
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.repository.TextStore
import com.femi.gallery.domain.text.DetectedText
import com.femi.gallery.domain.text.TextCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the text read from photos in one file in the app's private storage, rewritten through a
 * temporary file and a rename so a crash never leaves it half written. The file is excluded from
 * backups (see backup_rules.xml): what is written in your photos stays on the phone.
 */
@Singleton
class FileTextStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : TextStore {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    override suspend fun load(): List<DetectedText> = withContext(io) {
        val target = file
        if (!target.exists()) emptyList() else TextCodec.decode(target.readBytes()).orEmpty()
    }

    override suspend fun save(entries: List<DetectedText>) = withContext(io) {
        val target = file
        val temporary = File(target.parentFile, "$FILE_NAME.tmp")
        temporary.writeBytes(TextCodec.encode(entries))
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        Unit
    }

    private companion object {
        const val FILE_NAME = "texts.bin"
    }
}
