package com.femi.gallery.domain.geo

class PhotoPlaceIndex(
    entries: List<LocationEntry>,
    mapPlaces: List<MapPlace>,
) {
    private val cityMap: Map<String, Set<Long>> = run {
        val map = HashMap<String, HashSet<Long>>()
        if (mapPlaces.isNotEmpty()) {
            for (entry in entries) {
                val point = entry.place ?: continue
                val cityName = PhotoPlaceMatcher.nearestPlaceName(point, mapPlaces)
                if (cityName != null) {
                    map.getOrPut(cityName) { HashSet() }.add(entry.id)
                }
            }
        }
        map
    }

    fun idsMatching(term: String): Set<Long> {
        val wanted = term.lowercase().trim()
        if (wanted.isEmpty()) return emptySet()
        val result = HashSet<Long>()
        for ((city, ids) in cityMap) {
            if (city.contains(wanted)) {
                result.addAll(ids)
            }
        }
        return result
    }

    companion object {
        val Empty = PhotoPlaceIndex(emptyList(), emptyList())
    }
}
