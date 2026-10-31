package com.femi.gallery.data.clip

import com.femi.gallery.domain.clip.ClipIndex
import com.femi.gallery.domain.clip.DetectedEmbedding
import com.femi.gallery.domain.repository.ClipStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** The embeddings of the scanned photos, loaded once and replaced as the scan makes progress. */
@Singleton
class ClipIndexHolder @Inject constructor(
    private val store: ClipStore,
) {
    private val mutex = Mutex()

    @Volatile
    private var loaded: ClipIndex? = null

    /** Rises every time the index is replaced, so an answer remembered for an older index can be told from a current one. */
    @Volatile
    var version: Int = 0
        private set

    suspend fun current(): ClipIndex =
        loaded ?: mutex.withLock { loaded ?: ClipIndex(store.load()).also { loaded = it } }

    suspend fun publish(entries: List<DetectedEmbedding>) {
        val index = ClipIndex(entries)
        mutex.withLock {
            loaded = index
            version++
        }
    }
}
