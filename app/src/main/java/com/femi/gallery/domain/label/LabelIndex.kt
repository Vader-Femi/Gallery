package com.femi.gallery.domain.label

class LabelIndex(val entries: List<DetectedLabel>) {
    private val labelMap: Map<Long, List<String>> = run {
        val map = HashMap<Long, List<String>>()
        for (entry in entries) {
            val labels = entry.labels.split(" ").filter { it.isNotBlank() }
            if (labels.isNotEmpty()) map[entry.id] = labels
        }
        map
    }

    val readCount: Int get() = entries.size
    val withLabelCount: Int get() = labelMap.size

    /** The photos that have a label answering [term] (see [LabelMatcher]). */
    fun idsMatching(term: String): Set<Long> {
        val wanted = LabelMatcher.expand(term)
        if (wanted.isEmpty()) return emptySet()
        val result = HashSet<Long>()
        for ((id, labels) in labelMap) {
            if (labels.any { LabelMatcher.matches(it, wanted) }) result.add(id)
        }
        return result
    }

    companion object {
        val Empty = LabelIndex(emptyList())
    }
}
