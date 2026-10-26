package com.femi.gallery.domain.text

import java.text.Normalizer

/**
 * Turns text into the form it is compared in: lower case, without accents, with every run of other
 * characters (spaces, punctuation, line breaks) reduced to one space. "Café-Menu\nNº 4" and
 * "cafe menu n 4" are then the same text, which is what matters when text read from a photo (with its
 * stray line breaks and symbols) is searched for words the user types.
 */
object TextNormalizer {

    fun normalize(text: String): String {
        // The compatibility form also turns "º" into "o", the ligature "ﬁ" into "fi" and "m²" into "m2".
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD)
        val result = StringBuilder(decomposed.length)
        var pendingSpace = false
        for (char in decomposed) {
            when {
                Character.getType(char) == Character.NON_SPACING_MARK.toInt() -> Unit // the accent itself
                char.isLetterOrDigit() -> {
                    if (pendingSpace && result.isNotEmpty()) result.append(' ')
                    pendingSpace = false
                    result.append(char.lowercaseChar())
                }
                else -> pendingSpace = true
            }
        }
        return result.toString()
    }
}
