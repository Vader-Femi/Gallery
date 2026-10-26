package com.femi.gallery.data.clip

import com.femi.gallery.domain.clip.ClipIndex
import com.femi.gallery.domain.clip.DetectedEmbedding
import com.femi.gallery.domain.repository.ClipStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipIndexHolder @Inject constructor(
    private val store: ClipStore,
) {
    private val mutex = Mutex()

    @Volatile
    private var loaded: ClipIndex? = null

    private val answers = HashMap<String, Set<Long>>()

    suspend fun current(): ClipIndex =
        loaded ?: mutex.withLock { loaded ?: ClipIndex(store.load()).also { loaded = it } }

    suspend fun publish(entries: List<DetectedEmbedding>) {
        val index = ClipIndex(entries)
        mutex.withLock {
            loaded = index
            synchronized(answers) { answers.clear() }
        }
    }

    suspend fun idsMatching(queryEmbedding: FloatArray?, term: String): Set<Long> {
        val index = current()
        synchronized(answers) { answers[term]?.let { return it } }
        val found = index.idsMatching(queryEmbedding)
        synchronized(answers) { if (loaded === index) answers[term] = found }
        return found
    }
}
