package com.femi.gallery.domain.clip

class ClipIndex(val entries: List<DetectedEmbedding>) {
    private val map: Map<Long, FloatArray> = entries.associate { it.id to it.embedding }

    val readCount: Int get() = entries.size

    fun idsMatching(queryEmbedding: FloatArray?, threshold: Float = 0.25f, maxResults: Int = 500): Set<Long> {
        if (queryEmbedding == null || queryEmbedding.isEmpty() || map.isEmpty()) return emptySet()
        val scored = ArrayList<Pair<Long, Float>>(map.size)
        for ((id, emb) in map) {
            val score = CosineSimilarity.compute(queryEmbedding, emb)
            if (score >= threshold) {
                scored.add(id to score)
            }
        }
        scored.sortByDescending { it.second }
        return scored.take(maxResults).map { it.first }.toSet()
    }

    companion object {
        val Empty = ClipIndex(emptyList())
    }
}
