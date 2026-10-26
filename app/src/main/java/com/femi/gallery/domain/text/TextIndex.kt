package com.femi.gallery.domain.text

/**
 * What has been read from the photos so far, ready to be searched. Immutable, so the search can keep
 * using one while the scan builds the next.
 */
class TextIndex(val entries: List<DetectedText>) {

    /** Comparable form of the text of each photo that has any. */
    private val texts: Map<Long, String> = HashMap<Long, String>().also { map ->
        for (entry in entries) {
            if (entry.text.isBlank()) continue
            val normalized = TextNormalizer.normalize(entry.text)
            if (normalized.isNotEmpty()) map[entry.id] = normalized
        }
    }

    /** How many photos have been read. */
    val readCount: Int get() = entries.size

    /** How many of them contain text. */
    val withTextCount: Int get() = texts.size

    /**
     * The ids of the photos whose text contains [term]. Short words (one or two characters) only match
     * as whole words, because inside other words they would match nearly everything; longer ones match
     * anywhere, so "invoi" finds "invoice".
     */
    fun idsMatching(term: String): Set<Long> {
        val wanted = TextNormalizer.normalize(term)
        if (wanted.isEmpty()) return emptySet()
        val result = HashSet<Long>()
        if (wanted.length < WHOLE_WORD_BELOW) {
            val padded = " $wanted "
            for ((id, text) in texts) if (" $text ".contains(padded)) result.add(id)
        } else {
            for ((id, text) in texts) if (text.contains(wanted)) result.add(id)
        }
        return result
    }

    companion object {
        const val WHOLE_WORD_BELOW = 3
        val Empty = TextIndex(emptyList())
    }
}
