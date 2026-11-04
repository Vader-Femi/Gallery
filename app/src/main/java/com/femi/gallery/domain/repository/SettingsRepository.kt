package com.femi.gallery.domain.repository

import com.femi.gallery.domain.mirror.AlbumSortOption
import com.femi.gallery.domain.model.SlideshowSpeed
import com.femi.gallery.presentation.pictures.TimelineZoom
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val themeMode: Flow<Int>
    val autoplayVideos: Flow<Boolean>
    val muteVideos: Flow<Boolean>
    val backgroundScan: Flow<Boolean>
    val scanOnlyWhileCharging: Flow<Boolean>
    val slideshowSpeed: Flow<SlideshowSpeed>
    val slideshowShuffle: Flow<Boolean>
    val timelineZoom: Flow<TimelineZoom>
    val semanticSearch: Flow<Boolean>
    val semanticSearchStrictness: Flow<SemanticSearchStrictness>
    val albumSortOption: Flow<AlbumSortOption>
    val hiddenAlbums: Flow<Set<String>>

    suspend fun setThemeMode(mode: Int)
    suspend fun setAutoplayVideos(enabled: Boolean)
    suspend fun setMuteVideos(enabled: Boolean)
    suspend fun setBackgroundScan(enabled: Boolean)
    suspend fun setScanOnlyWhileCharging(enabled: Boolean)
    suspend fun setSlideshowSpeed(speed: SlideshowSpeed)
    suspend fun setSlideshowShuffle(enabled: Boolean)
    suspend fun setTimelineZoom(zoom: TimelineZoom)
    suspend fun setSemanticSearch(enabled: Boolean)
    suspend fun setSemanticSearchStrictness(strictness: SemanticSearchStrictness)
    suspend fun setAlbumSortOption(option: AlbumSortOption)
    suspend fun setHiddenAlbums(albums: Set<String>)

    // For synchronous reads if absolutely necessary during init
    fun getThemeModeSync(): Int
    fun getSemanticSearchSync(): Boolean
    fun getBackgroundScanSync(): Boolean
    fun getScanOnlyWhileChargingSync(): Boolean
    fun getTimelineZoomSync(): TimelineZoom
}

enum class SemanticSearchStrictness {
    Relaxed,
    Normal,
    Strict
}