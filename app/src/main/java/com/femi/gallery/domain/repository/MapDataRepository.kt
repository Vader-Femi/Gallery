package com.femi.gallery.domain.repository

import com.femi.gallery.domain.geo.MapData

interface MapDataRepository {
    /** The land, borders and places the map draws. Loaded once and kept. */
    suspend fun load(): MapData
}
