package com.femi.gallery.data.clip

import android.content.Context
import android.util.Log
import com.femi.gallery.domain.clip.DescriptionQuery
import com.femi.gallery.domain.repository.ImageEmbedder
import com.femi.gallery.presentation.settings.ThemePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds photos by what they show, from the words of a search read as a description ("a dog running on a beach").
 *
 * It only ever adds matches: the words are matched by name, album, text in the photo, colour, labels and place
 * exactly as before, and the photos found here are added to those. It does nothing until an image/text model is
 * installed and the photos have been scanned with it, or when the person turned it off.
 */
@Singleton
class SemanticSearch @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val embedder: ImageEmbedder,
    private val index: ClipIndexHolder,
) {
    /** The answers to the last few descriptions, so that reloading a list does not embed the text and compare again. */
    private val answers = object : LinkedHashMap<String, Set<Long>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Set<Long>>?) = size > MAX_ANSWERS
    }

    /** The photos matching the description made of [words], closest first; empty when this search does not apply. */
    suspend fun idsFor(words: List<String>): Set<Long> {
        if (!embedder.isAvailable || !ThemePreferences.getSemanticSearch(context)) return emptySet()
        val phrase = words.joinToString(" ").trim()
        if (!DescriptionQuery.isWorthDescribing(phrase)) return emptySet()
        val clip = index.current()
        if (clip.embeddedCount == 0) return emptySet()

        val key = "${index.version}|$phrase"
        synchronized(answers) { answers[key]?.let { return it } }
        val query = embedder.embedText(phrase) ?: return emptySet()
        val scores = clip.scores(query)
        val found = clip.search(query).toSet()
        Log.d(TAG, "\"$phrase\": ${found.size} photos, best ${scores?.maxOrNull()}")
        synchronized(answers) { answers[key] = found }
        return found
    }

    private companion object {
        const val TAG = "ClipSearch"
        const val MAX_ANSWERS = 8
    }
}
