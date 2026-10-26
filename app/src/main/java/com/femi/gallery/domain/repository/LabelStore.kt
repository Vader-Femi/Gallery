package com.femi.gallery.domain.repository

import com.femi.gallery.domain.label.DetectedLabel

interface LabelStore {
    suspend fun load(): List<DetectedLabel>
    suspend fun save(entries: List<DetectedLabel>)
}
