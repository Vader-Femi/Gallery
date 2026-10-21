package com.femi.gallery.domain.repository

import com.femi.gallery.domain.geo.LocationEntry

/** Where the scan's results are kept between runs. */
interface LocationStore {
    /** Everything saved, or an empty list when there is nothing (or it is damaged). */
    suspend fun load(): List<LocationEntry>

    suspend fun save(entries: List<LocationEntry>)
}
