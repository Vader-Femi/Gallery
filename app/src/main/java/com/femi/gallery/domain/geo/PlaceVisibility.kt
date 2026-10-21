package com.femi.gallery.domain.geo

/** How far the map must be zoomed in before a place gets a label: bigger places show sooner. */
object PlaceVisibility {

    /** The lowest zoom level at which a place with [populationThousands] inhabitants is labelled. */
    fun minZoom(populationThousands: Int): Double = when {
        populationThousands >= 10_000 -> 2.0
        populationThousands >= 5_000 -> 3.0
        populationThousands >= 2_000 -> 4.0
        populationThousands >= 1_000 -> 4.8
        populationThousands >= 500 -> 5.6
        populationThousands >= 200 -> 6.5
        else -> 7.5
    }
}
