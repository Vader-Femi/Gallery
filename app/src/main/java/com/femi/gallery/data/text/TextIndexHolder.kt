package com.femi.gallery.data.text

import com.femi.gallery.domain.repository.TextStore
import com.femi.gallery.domain.text.DetectedText
import com.femi.gallery.domain.text.TextIndex
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The text read from photos, held in memory for searching: read from the store the first time it is
 * needed and replaced whenever the scan publishes more.
 */
@Singleton
class TextIndexHolder @Inject constructor(
    private val store: TextStore,
) {
    private val mutex = Mutex()

    @Volatile
    private var loaded: TextIndex? = null

    /** What was asked for before, until the index changes: a search asks for the same words page after page. */
    private val answers = HashMap<String, Set<Long>>()

    suspend fun current(): TextIndex =
        loaded ?: mutex.withLock { loaded ?: TextIndex(store.load()).also { loaded = it } }

    suspend fun publish(entries: List<DetectedText>) {
        val index = TextIndex(entries)
        mutex.withLock {
            loaded = index
            synchronized(answers) { answers.clear() }
        }
    }

    /** The ids of the photos whose text contains [term]. */
    suspend fun idsMatching(term: String): Set<Long> {
        val index = current()
        synchronized(answers) { answers[term]?.let { return it } }
        val found = index.idsMatching(term)
        // Only keep the answer if the index has not been replaced meanwhile.
        synchronized(answers) { if (loaded === index) answers[term] = found }
        return found
    }
}
