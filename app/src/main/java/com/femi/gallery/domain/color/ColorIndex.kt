package com.femi.gallery.domain.color

class ColorIndex(val entries: List<DetectedColor>) {
    private val colorMap: Map<Long, Set<String>> = run {
        val map = HashMap<Long, Set<String>>()
        for (entry in entries) {
            if (entry.colors.isBlank()) continue
            val tags = entry.colors.split(" ").filter { it.isNotBlank() }.toSet()
            if (tags.isNotEmpty()) {
                map[entry.id] = tags
            }
        }
        map
    }

    val readCount: Int get() = entries.size
    val withColorCount: Int get() = colorMap.size

    fun idsMatching(term: String): Set<Long> {
        val wanted = term.lowercase().trim()
        if (wanted.isEmpty()) return emptySet()
        val result = HashSet<Long>()
        for ((id, tags) in colorMap) {
            if (tags.contains(wanted)) result.add(id)
        }
        return result
    }

    companion object {
        val Empty = ColorIndex(emptyList())
    }
}
