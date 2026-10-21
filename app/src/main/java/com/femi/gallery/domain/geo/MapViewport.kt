package com.femi.gallery.domain.geo

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Which part of the world map is on screen: the point at the centre and the zoom level. At zoom 0
 * the whole world is [TILE_SIZE] pixels wide and every zoom level doubles that.
 *
 * The screen size is passed to each function rather than stored, so the same viewport survives a
 * rotation. The map does not wrap around: panning stops at the edges of the world.
 */
data class MapViewport(
    val centerLatitude: Double,
    val centerLongitude: Double,
    val zoom: Double,
) {
    private val centerX: Double get() = Mercator.x(centerLongitude)
    private val centerY: Double get() = Mercator.y(centerLatitude)

    fun screenX(longitude: Double, width: Double): Double =
        (Mercator.x(longitude) - centerX) * worldSize(zoom) + width / 2

    fun screenY(latitude: Double, height: Double): Double =
        (Mercator.y(latitude) - centerY) * worldSize(zoom) + height / 2

    fun longitudeAt(screenX: Double, width: Double): Double =
        Mercator.longitude(centerX + (screenX - width / 2) / worldSize(zoom))

    fun latitudeAt(screenY: Double, height: Double): Double =
        Mercator.latitude(centerY + (screenY - height / 2) / worldSize(zoom))

    /** The viewport after the map is dragged by ([dx], [dy]) pixels (the map follows the finger). */
    fun panned(dx: Double, dy: Double, width: Double, height: Double): MapViewport {
        val size = worldSize(zoom)
        return atWorld(centerX - dx / size, centerY - dy / size, zoom, width, height)
    }

    /** The viewport at [newZoom] that keeps the point under ([anchorX], [anchorY]) where it is. */
    fun zoomedAbout(
        anchorX: Double,
        anchorY: Double,
        newZoom: Double,
        width: Double,
        height: Double,
    ): MapViewport {
        val target = newZoom.coerceIn(minZoom(width, height), MAX_ZOOM)
        val oldSize = worldSize(zoom)
        val newSize = worldSize(target)
        val anchorWorldX = centerX + (anchorX - width / 2) / oldSize
        val anchorWorldY = centerY + (anchorY - height / 2) / oldSize
        return atWorld(
            anchorWorldX - (anchorX - width / 2) / newSize,
            anchorWorldY - (anchorY - height / 2) / newSize,
            target,
            width,
            height,
        )
    }

    /** The part of the world on screen, grown by [marginPixels] on every side. */
    fun visibleBox(width: Double, height: Double, marginPixels: Double = 0.0): GeoBox {
        val west = longitudeAt(-marginPixels, width).coerceIn(-180.0, 180.0)
        val east = longitudeAt(width + marginPixels, width).coerceIn(-180.0, 180.0)
        val north = latitudeAt(-marginPixels, height)
        val south = latitudeAt(height + marginPixels, height)
        return GeoBox(south.coerceIn(-90.0, 90.0), min(west, east), north.coerceIn(-90.0, 90.0), max(west, east))
    }

    companion object {
        const val TILE_SIZE = 256.0
        const val MAX_ZOOM = 20.0

        /** The zoom used to look at a single place. */
        const val SINGLE_PLACE_ZOOM = 15.0

        val World = MapViewport(0.0, 0.0, 0.0)

        fun worldSize(zoom: Double): Double = TILE_SIZE * 2.0.pow(zoom)

        /** The smallest zoom at which the world still covers the whole screen. */
        fun minZoom(width: Double, height: Double): Double =
            log2(max(width, height) / TILE_SIZE).coerceAtLeast(0.0)

        /** The viewport that shows all of [box], leaving [paddingPixels] free around it. */
        fun fit(
            box: GeoBox,
            width: Double,
            height: Double,
            paddingPixels: Double = 0.0,
            maxZoom: Double = SINGLE_PLACE_ZOOM,
        ): MapViewport {
            val west = Mercator.x(box.west)
            val east = Mercator.x(box.east) + if (box.crossesAntimeridian) 1.0 else 0.0
            val north = Mercator.y(box.north)
            val south = Mercator.y(box.south)
            val spanX = east - west
            val spanY = south - north
            val availableWidth = max(1.0, width - 2 * paddingPixels)
            val availableHeight = max(1.0, height - 2 * paddingPixels)
            val wanted = if (spanX <= MIN_SPAN && spanY <= MIN_SPAN) {
                maxZoom
            } else {
                min(
                    log2(availableWidth / (max(spanX, MIN_SPAN) * TILE_SIZE)),
                    log2(availableHeight / (max(spanY, MIN_SPAN) * TILE_SIZE)),
                )
            }
            val zoom = wanted.coerceIn(minZoom(width, height), maxZoom)
            val centerX = (west + spanX / 2).let { if (it >= 1.0) it - 1.0 else it }
            return atWorld(centerX, north + spanY / 2, zoom, width, height)
        }

        private const val MIN_SPAN = 1e-9

        private fun log2(value: Double): Double = ln(value) / ln(2.0)

        /** A viewport centred on world position ([x], [y]), kept so that no empty space shows. */
        private fun atWorld(x: Double, y: Double, zoom: Double, width: Double, height: Double): MapViewport {
            val size = worldSize(zoom)
            return MapViewport(
                centerLatitude = Mercator.latitude(clampCentre(y, height / 2 / size)),
                centerLongitude = Mercator.longitude(clampCentre(x, width / 2 / size)),
                zoom = zoom,
            )
        }

        private fun clampCentre(value: Double, halfSpan: Double): Double =
            if (halfSpan >= 0.5) 0.5 else value.coerceIn(halfSpan, 1.0 - halfSpan)
    }
}
