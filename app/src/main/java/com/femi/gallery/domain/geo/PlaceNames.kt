package com.femi.gallery.domain.geo

import java.text.Normalizer

/** How place names are compared when searching: ignoring case and accents, word by word. */
object PlaceNames {

    private val marks = Regex("\\p{Mn}+")
    private val separators = Regex("[^a-z0-9]+")

    /** Lower case, without accents: "São Paulo" becomes "sao paulo". */
    fun fold(text: String): String =
        marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
            .lowercase()
            .replace("ß", "ss")
            .replace("ø", "o")
            .replace("æ", "ae")
            .replace("ł", "l")
            .replace("đ", "d")

    /**
     * Whether the folded search word [term] names the folded [placeName]: it equals one of the name's
     * words, or begins one when it has three letters or more ("pau" finds "sao paulo").
     */
    fun matches(placeName: String, term: String): Boolean {
        if (term.isEmpty()) return false
        return placeName.split(separators).any { word ->
            word.isNotEmpty() && (word == term || (term.length >= MIN_PREFIX && word.startsWith(term)))
        }
    }

    private const val MIN_PREFIX = 3
}
