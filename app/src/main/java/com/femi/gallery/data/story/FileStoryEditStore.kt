package com.femi.gallery.data.story

import android.content.Context
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.model.StoryEdit
import com.femi.gallery.domain.model.StoryEditCodec
import com.femi.gallery.domain.repository.StoryEditStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

/** The story edits in one small file in the app's private storage, read once and kept in memory. */
@Singleton
class FileStoryEditStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : StoryEditStore {

    private val lock = Mutex()
    private val state = MutableStateFlow<List<StoryEdit>?>(null)

    private val file: File get() = File(context.filesDir, FILE_NAME)

    override val edits: Flow<List<StoryEdit>> = flow {
        lock.withLock { loadIfNeeded() }
        emitAll(state.filterNotNull())
    }

    override suspend fun update(change: (List<StoryEdit>) -> List<StoryEdit>) {
        lock.withLock {
            val updated = change(loadIfNeeded())
            write(updated)
            state.value = updated
        }
    }

    private suspend fun loadIfNeeded(): List<StoryEdit> {
        state.value?.let { return it }
        val loaded = withContext(io) {
            val target = file
            if (target.exists()) StoryEditCodec.decode(target.readBytes()).orEmpty() else emptyList()
        }
        state.value = loaded
        return loaded
    }

    private suspend fun write(edits: List<StoryEdit>) = withContext(io) {
        val target = file
        val temporary = File(target.parentFile, "$FILE_NAME.tmp")
        temporary.writeBytes(StoryEditCodec.encode(edits))
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        Unit
    }

    private companion object {
        const val FILE_NAME = "story_edits.bin"
    }
}
