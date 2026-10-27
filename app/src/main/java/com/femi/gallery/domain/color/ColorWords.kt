package com.femi.gallery.domain.color

/**
 * Turns the colour words people type into the tags [ColorClassifier] produces. The classifier knows
 * a dozen colours; people say "navy", "violet", "beige", "colour".
 */
object ColorWords {

    /** The tag [word] stands for, or null when it is not a colour word. */
    fun tagFor(word: String): String? {
        val w = word.lowercase().trim()
        return if (w in TAGS) w else SYNONYMS[w]
    }

    private val TAGS = setOf(
        "red", "orange", "yellow", "green", "blue", "purple", "pink", "brown",
        "black", "white", "gray", "grey", "dark", "light",
    )

    private val SYNONYMS = mapOf(
        "crimson" to "red", "scarlet" to "red", "maroon" to "red", "burgundy" to "red",
        "gold" to "yellow", "golden" to "yellow", "lemon" to "yellow",
        "lime" to "green", "olive" to "green", "emerald" to "green", "teal" to "green",
        "navy" to "blue", "cyan" to "blue", "turquoise" to "blue", "aqua" to "blue", "azure" to "blue",
        "violet" to "purple", "lavender" to "purple", "lilac" to "purple", "magenta" to "purple",
        "rose" to "pink", "salmon" to "pink",
        "tan" to "brown", "beige" to "brown", "chocolate" to "brown", "bronze" to "brown",
        "silver" to "gray", "charcoal" to "gray",
        "bright" to "light", "pale" to "light",
        "night" to "dark", "shadow" to "dark", "dim" to "dark",
    )
}
