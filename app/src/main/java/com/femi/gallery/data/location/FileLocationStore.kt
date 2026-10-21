package com.femi.gallery.data.location

import android.content.Context
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.geo.LocationCodec
import com.femi.gallery.domain.geo.LocationEntry
import com.femi.gallery.domain.repository.LocationStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the scan results in one small file in the app's private storage. It is rewritten through a
 * temporary file and a rename, so a crash or a full disk never leaves a half-written file behind.
 * The file is excluded from backups (see backup_rules.xml): where photos were taken stays on the phone.
 */
@Singleton
class FileLocationStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : LocationStore {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    override suspend fun load(): List<LocationEntry> = withContext(io) {
        val target = file
        if (!target.exists()) emptyList() else LocationCodec.decode(target.readBytes()).orEmpty()
    }

    override suspend fun save(entries: List<LocationEntry>) = withContext(io) {
        val target = file
        val temporary = File(target.parentFile, "$FILE_NAME.tmp")
        temporary.writeBytes(LocationCodec.encode(entries))
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        Unit
    }

    private companion object {
        const val FILE_NAME = "locations.bin"
    }
}
