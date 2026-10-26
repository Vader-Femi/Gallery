package com.femi.gallery.domain.repository

import com.femi.gallery.domain.color.DetectedColor

interface ColorStore {
    suspend fun load(): List<DetectedColor>
    suspend fun save(entries: List<DetectedColor>)
}
