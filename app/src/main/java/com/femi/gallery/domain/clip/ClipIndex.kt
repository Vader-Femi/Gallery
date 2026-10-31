package com.femi.gallery.domain.clip

/**
 * The embeddings of the scanned photos, laid out in one array so that comparing a description with every
 * photo is a single pass over memory. Photos whose embedding is empty (unreadable) or of a different size
 * than the first one (made by another model) are left out of the comparison.
 */
class ClipIndex(val entries: List<DetectedEmbedding>) {

    /** How many numbers each embedding has; 0 when there is none. */
    val dimension: Int = entries.firstOrNull { it.embedding.isNotEmpty() }?.embedding?.size ?: 0

    private val ids: LongArray
    private val data: ByteArray

    init {
        val usable = entries.filter { dimension > 0 && it.embedding.size == dimension }
        ids = LongArray(usable.size) { usable[it].id }
        data = ByteArray(usable.size * dimension)
        usable.forEachIndexed { i, entry -> entry.embedding.copyInto(data, i * dimension) }
    }

    /** How many photos were looked at (including those that could not be read). */
    val readCount: Int get() = entries.size

    /** How many photos can be compared. */
    val embeddedCount: Int get() = ids.size

    /** The ids of the comparable photos, in the order of [scores]. */
    fun idsInOrder(): LongArray = ids

    /** The cosine between [query] and every comparable photo; null when the query is not of this index's size. */
    fun scores(query: FloatArray): FloatArray? {
        if (dimension == 0 || query.size != dimension || ids.isEmpty()) return null
        val unit = ClipQuantizer.normalize(query)
        return FloatArray(ids.size) { ClipQuantizer.cosine(unit, data, it * dimension) }
    }

    /** The photos that match the description whose embedding is [query], closest first (see [SemanticSelection]). */
    fun search(query: FloatArray, config: SemanticSelection.Config = SemanticSelection.Config()): List<Long> {
        val s = scores(query) ?: return emptyList()
        return SemanticSelection.select(ids, s, config)
    }

    companion object {
        val Empty = ClipIndex(emptyList())
    }
}
