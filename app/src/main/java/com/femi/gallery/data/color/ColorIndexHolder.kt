package com.femi.gallery.data.color

import com.femi.gallery.domain.color.ColorIndex
import com.femi.gallery.domain.color.DetectedColor
import com.femi.gallery.domain.repository.ColorStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColorIndexHolder @Inject constructor(
    private val store: ColorStore,
) {
    private val mutex = Mutex()

    @Volatile
    private var loaded: ColorIndex? = null

    private val answers = HashMap<String, Set<Long>>()

    suspend fun current(): ColorIndex =
        loaded ?: mutex.withLock { loaded ?: ColorIndex(store.load()).also { loaded = it } }

    suspend fun publish(entries: List<DetectedColor>) {
        val index = ColorIndex(entries)
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
