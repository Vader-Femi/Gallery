package com.femi.gallery.domain.clip

/**
 * Which photos count as matching a description, given how close each one is to it.
 *
 * Closeness is not an absolute scale: a very good match has a cosine of about 0.3 and unrelated photos about
 * 0.15, and the numbers depend on the model. So a photo must be close in two ways: not far behind the best
 * match for this description ([margin]), and above a floor ([minScore]) under which nothing is a match at all
 * (a description that matches nothing in the library returns nothing instead of the least bad photos).
 * At most [maxResults] are kept, the closest first.
 */
object SemanticSelection {

    data class Config(
        val minScore: Float = 0.20f,
        val margin: Float = 0.05f,
        val maxResults: Int = 400,
    )

    /** The ids of the photos that match, closest first. [ids] and [scores] go together. */
    fun select(ids: LongArray, scores: FloatArray, config: Config = Config()): List<Long> {
        if (ids.isEmpty() || ids.size != scores.size) return emptyList()
        var best = Float.NEGATIVE_INFINITY
        for (s in scores) if (s > best) best = s
        if (best < config.minScore) return emptyList()
        val threshold = maxOf(config.minScore, best - config.margin)
        val matching = ArrayList<Int>()
        for (i in scores.indices) if (scores[i] >= threshold) matching.add(i)
        matching.sortByDescending { scores[it] }
        return matching.take(config.maxResults).map { ids[it] }
    }
}
