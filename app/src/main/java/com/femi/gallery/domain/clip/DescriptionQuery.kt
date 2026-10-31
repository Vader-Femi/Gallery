package com.femi.gallery.domain.clip

/** Whether typed search words read like a description of a photo, as opposed to a number, a date or a file name. */
object DescriptionQuery {

    /**
     * True when [phrase] has a word of at least three letters and at least half of its letters-and-digits are
     * letters. "sunset over the sea" and "dog" qualify; "2019", "oct 15" and "img_0042" do not.
     */
    fun isWorthDescribing(phrase: String): Boolean {
        val letters = phrase.count { it.isLetter() }
        val digits = phrase.count { it.isDigit() }
        if (letters == 0 || letters < digits) return false
        return Regex("\\p{L}{3,}").containsMatchIn(phrase)
    }
}
