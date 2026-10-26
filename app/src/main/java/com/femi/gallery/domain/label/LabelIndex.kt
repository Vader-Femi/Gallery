package com.femi.gallery.domain.label

class LabelIndex(val entries: List<DetectedLabel>) {
    private val labelMap: Map<Long, Set<String>> = run {
        val map = HashMap<Long, Set<String>>()
        for (entry in entries) {
            if (entry.labels.isBlank()) continue
            val tags = entry.labels.split(" ").filter { it.isNotBlank() }.toSet()
            if (tags.isNotEmpty()) {
                map[entry.id] = tags
            }
        }
        map
    }

    val readCount: Int get() = entries.size
    val withLabelCount: Int get() = labelMap.size

    fun idsMatching(term: String): Set<Long> {
        val wanted = term.lowercase().trim()
        if (wanted.isEmpty()) return emptySet()
        val result = HashSet<Long>()
        for ((id, tags) in labelMap) {
            if (tags.any { it.contains(wanted) }) result.add(id)
        }
        return result
    }

    companion object {
        val Empty = LabelIndex(emptyList())
    }
}
