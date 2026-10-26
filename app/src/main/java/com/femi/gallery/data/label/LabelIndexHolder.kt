package com.femi.gallery.data.label

import com.femi.gallery.domain.label.DetectedLabel
import com.femi.gallery.domain.label.LabelIndex
import com.femi.gallery.domain.repository.LabelStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LabelIndexHolder @Inject constructor(
    private val store: LabelStore,
) {
    private val mutex = Mutex()

    @Volatile
    private var loaded: LabelIndex? = null

    private val answers = HashMap<String, Set<Long>>()

    suspend fun current(): LabelIndex =
        loaded ?: mutex.withLock { loaded ?: LabelIndex(store.load()).also { loaded = it } }

    suspend fun publish(entries: List<DetectedLabel>) {
        val index = LabelIndex(entries)
        mutex.withLock {
            loaded = index
            synchronized(answers) { answers.clear() }
        }
    }

    suspend fun idsMatching(term: String): Set<Long> {
        val index = current()
        synchronized(answers) { answers[term]?.let { return it } }
        val found = index.idsMatching(term)
        synchronized(answers) { if (loaded === index) answers[term] = found }
        return found
    }
}
