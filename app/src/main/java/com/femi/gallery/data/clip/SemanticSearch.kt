package com.femi.gallery.data.clip

import android.util.Log
import com.femi.gallery.domain.clip.DescriptionQuery
import com.femi.gallery.domain.repository.ImageEmbedder
import com.femi.gallery.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
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
    private val embedder: ImageEmbedder,
    private val index: ClipIndexHolder,
    private val settingsRepository: SettingsRepository,
) {
    /** The answers to the last few descriptions, so that reloading a list does not embed the text and compare again. */
    private val answers = object : LinkedHashMap<String, Set<Long>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Set<Long>>?) = size > MAX_ANSWERS
    }

    /** The photos matching the description made of [words], closest first; empty when this search does not apply. */
    suspend fun idsFor(words: List<String>): Set<Long> {
        if (!embedder.isAvailable || !settingsRepository.semanticSearch.first()) return emptySet()
        val phrase = words.joinToString(" ").trim()
        if (!DescriptionQuery.isWorthDescribing(phrase)) return emptySet()
        val clip = index.current()
        if (clip.embeddedCount == 0) return emptySet()

        val key = "${index.version}|$phrase"
        synchronized(answers) { answers[key]?.let { return it } }
        
        // "a photo of ..." wrapping for short words: CLIP often understands a short word better 
        // when presented as a sentence instead of a naked noun.
        val searchPhrase = if (words.size == 1 && phrase.length >= 3 && !phrase.startsWith("a ") && !phrase.startsWith("an ")) {
            "a photo of a $phrase"
        } else {
            phrase
        }
        
        val strictness = settingsRepository.semanticSearchStrictness.first()
        val config = when (strictness) {
            com.femi.gallery.domain.repository.SemanticSearchStrictness.Relaxed -> com.femi.gallery.domain.clip.SemanticSelection.Config(minScore = 0.17f, margin = 0.08f)
            com.femi.gallery.domain.repository.SemanticSearchStrictness.Normal -> com.femi.gallery.domain.clip.SemanticSelection.Config(minScore = 0.20f, margin = 0.05f)
            com.femi.gallery.domain.repository.SemanticSearchStrictness.Strict -> com.femi.gallery.domain.clip.SemanticSelection.Config(minScore = 0.23f, margin = 0.03f)
        }
        
        val query = embedder.embedText(searchPhrase) ?: return emptySet()
        val scores = clip.scores(query)
        val found = clip.search(query, config).toSet()
        Log.d(TAG, "\"$searchPhrase\": ${found.size} photos, best ${scores?.maxOrNull()} (strictness=${strictness.name})")
        synchronized(answers) { answers[key] = found }
        return found
    }

    private companion object {
        const val TAG = "ClipSearch"
        const val MAX_ANSWERS = 8
    }
}
