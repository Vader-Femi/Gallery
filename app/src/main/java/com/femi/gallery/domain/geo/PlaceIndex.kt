package com.femi.gallery.domain.geo

/**
 * What the scan knows: the place of each photo that has one. Immutable, so a screen can keep using
 * one while the scan builds the next.
 */
class PlaceIndex(val entries: List<LocationEntry>) {

    private val places: Map<Long, GeoPoint> = HashMap<Long, GeoPoint>().also { map ->
        for (entry in entries) entry.place?.let { map[entry.id] = it }
    }

    /** How many photos have a place. */
    val placedCount: Int get() = places.size

    fun placeOf(id: Long): GeoPoint? = places[id]

    /** The ids of the photos taken inside [box]. */
    fun idsIn(box: GeoBox): Set<Long> {
        val result = HashSet<Long>()
        for ((id, place) in places) {
            if (box.contains(place.latitude, place.longitude)) result.add(id)
        }
        return result
    }

    companion object {
        val Empty = PlaceIndex(emptyList())
    }
}
