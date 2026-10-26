package com.femi.gallery.domain.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object PhotoPlaceMatcher {
    fun nearestPlaceName(point: GeoPoint, places: List<MapPlace>, maxDistanceKm: Double = 100.0): String? {
        if (places.isEmpty()) return null
        var nearestName: String? = null
        var minDistance = Double.MAX_VALUE

        for (place in places) {
            val dist = haversineKm(point.latitude, point.longitude, place.latitude, place.longitude)
            if (dist < minDistance) {
                minDistance = dist
                nearestName = place.name
            }
        }

        return if (minDistance <= maxDistanceKm) nearestName?.lowercase() else null
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * asin(sqrt(a))
        return r * c
    }
}
