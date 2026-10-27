package com.femi.gallery.domain.model

/** What may be typed as a story's title. */
object StoryTitle {
    const val MAX_LENGTH = 60

    /** [input] with its spaces tidied and cut to [MAX_LENGTH] characters; null when nothing is left. */
    fun clean(input: String): String? {
        val tidy = input.trim().replace(Regex("\\s+"), " ").take(MAX_LENGTH).trim()
        return tidy.ifEmpty { null }
    }
}
