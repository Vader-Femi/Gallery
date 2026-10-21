package com.femi.gallery.domain.geo

/**
 * A line or the outline of an area, as points already projected onto the unit square (see
 * [Mercator]) so that drawing it needs no trigonometry. Areas are not closed explicitly: the last
 * point joins the first when the polyline is drawn as an area.
 */
class MapPolyline(val xs: DoubleArray, val ys: DoubleArray) {
    val minX: Double
    val maxX: Double
    val minY: Double
    val maxY: Double

    init {
        require(xs.size == ys.size) { "Arrays must have the same size" }
        var loX = Double.MAX_VALUE
        var hiX = -Double.MAX_VALUE
        var loY = Double.MAX_VALUE
        var hiY = -Double.MAX_VALUE
        for (i in xs.indices) {
            if (xs[i] < loX) loX = xs[i]
            if (xs[i] > hiX) hiX = xs[i]
            if (ys[i] < loY) loY = ys[i]
            if (ys[i] > hiY) hiY = ys[i]
        }
        minX = loX
        maxX = hiX
        minY = loY
        maxY = hiY
    }

    val size: Int get() = xs.size
}

/** A town or city that is labelled on the map. [x] and [y] are its projected position. */
class MapPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** Inhabitants, in thousands. */
    val population: Int,
) {
    val x: Double = Mercator.x(longitude)
    val y: Double = Mercator.y(latitude)
}

/** Everything the map draws besides the photos. */
class MapData(
    /** Outlines of land and of islands and lakes in it; drawn as one area with holes. */
    val land: List<MapPolyline>,
    val borders: List<MapPolyline>,
    /** Largest first. */
    val places: List<MapPlace>,
) {
    companion object {
        val Empty = MapData(emptyList(), emptyList(), emptyList())
    }
}
