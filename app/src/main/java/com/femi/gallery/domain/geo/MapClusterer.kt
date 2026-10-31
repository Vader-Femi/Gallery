package com.femi.gallery.domain.geo

/**
 * Groups items that would overlap on screen. The map is cut into square cells of [DEFAULT_CELL_PIXELS]
 * pixels at the current zoom, and the items in one cell form one cluster. Zooming in makes cells
 * cover less ground, so clusters split apart.
 *
 * Cells never overlap, so the box of a cluster contains none of the items of another cluster:
 * searching a cluster's [GeoCluster.box] finds exactly [GeoCluster.count] items.
 */
object MapClusterer {
    const val DEFAULT_CELL_PIXELS = 72.0

    private class Cell(var count: Int, var coverIndex: Int) {
        var sumLatitude = 0.0
        var sumLongitude = 0.0
        var south = Double.MAX_VALUE
        var north = -Double.MAX_VALUE
        var west = Double.MAX_VALUE
        var east = -Double.MAX_VALUE
    }

    /**
     * The clusters at [zoom], largest first. Only items inside [within] are considered when it is
     * given (pass the visible area, grown by a margin, to keep this cheap while panning).
     */
    fun cluster(
        media: LocatedMedia,
        zoom: Double,
        cellPixels: Double = DEFAULT_CELL_PIXELS,
        within: GeoBox? = null,
    ): List<GeoCluster> {
        val cellsAcross = MapViewport.worldSize(zoom) / cellPixels
        val cells = HashMap<Long, Cell>()
        for (i in 0 until media.size) {
            val latitude = media.latitudes[i]
            val longitude = media.longitudes[i]
            if (within != null && !within.contains(latitude, longitude)) continue

            val column = (media.xs[i] * cellsAcross).toLong()
            val row = (media.ys[i] * cellsAcross).toLong()
            val key = (column shl 32) xor (row and 0xFFFFFFFFL)
            val cell = cells.getOrPut(key) { Cell(0, i) }
            cell.count++
            cell.sumLatitude += latitude
            cell.sumLongitude += longitude
            if (latitude < cell.south) cell.south = latitude
            if (latitude > cell.north) cell.north = latitude
            if (longitude < cell.west) cell.west = longitude
            if (longitude > cell.east) cell.east = longitude
            if (media.dates[i] > media.dates[cell.coverIndex]) cell.coverIndex = i
        }
        return cells.values
            .map { cell ->
                GeoCluster(
                    latitude = cell.sumLatitude / cell.count,
                    longitude = cell.sumLongitude / cell.count,
                    count = cell.count,
                    box = GeoBox(cell.south, cell.west, cell.north, cell.east),
                    coverId = media.ids[cell.coverIndex],
                    coverUri = media.coverUris.getOrElse(cell.coverIndex) { "" },
                )
            }
            .sortedWith(compareByDescending<GeoCluster> { it.count }.thenBy { it.coverId })
    }
}
