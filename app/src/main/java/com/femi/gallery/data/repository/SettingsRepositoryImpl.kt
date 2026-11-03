package com.femi.gallery.data.repository

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.femi.gallery.domain.mirror.AlbumSortOption
import com.femi.gallery.domain.model.SlideshowSpeed
import com.femi.gallery.domain.repository.SettingsRepository
import com.femi.gallery.presentation.pictures.TimelineZoom
import com.femi.gallery.presentation.settings.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val dataStore = context.dataStore

    companion object {
        private val KEY_THEME_MODE = intPreferencesKey("theme_mode")
        private val KEY_AUTOPLAY_VIDEOS = booleanPreferencesKey("autoplay_videos")
        private val KEY_MUTE_VIDEOS = booleanPreferencesKey("mute_videos")
        private val KEY_BACKGROUND_SCAN = booleanPreferencesKey("background_scan")
        private val KEY_SCAN_ONLY_CHARGING = booleanPreferencesKey("scan_only_while_charging")
        private val KEY_SLIDESHOW_SPEED = stringPreferencesKey("slideshow_speed")
        private val KEY_SLIDESHOW_SHUFFLE = booleanPreferencesKey("slideshow_shuffle")
        private val KEY_TIMELINE_ZOOM = stringPreferencesKey("timeline_zoom")
        private val KEY_SEMANTIC_SEARCH = booleanPreferencesKey("semantic_search")
        private val KEY_ALBUM_SORT = stringPreferencesKey("album_sort_option")
        private val KEY_HIDDEN_ALBUMS = stringSetPreferencesKey("hidden_albums")
    }

    override val themeMode: Flow<Int> = dataStore.data.map {
        it[KEY_THEME_MODE] ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    override val autoplayVideos: Flow<Boolean> = dataStore.data.map {
        it[KEY_AUTOPLAY_VIDEOS] ?: true
    }

    override val muteVideos: Flow<Boolean> = dataStore.data.map {
        it[KEY_MUTE_VIDEOS] ?: true
    }

    override val backgroundScan: Flow<Boolean> = dataStore.data.map {
        it[KEY_BACKGROUND_SCAN] ?: true
    }

    override val scanOnlyWhileCharging: Flow<Boolean> = dataStore.data.map {
        it[KEY_SCAN_ONLY_CHARGING] ?: true
    }

    override val slideshowSpeed: Flow<SlideshowSpeed> = dataStore.data.map {
        val name = it[KEY_SLIDESHOW_SPEED] ?: SlideshowSpeed.Default.name
        try { SlideshowSpeed.valueOf(name) } catch (e: Exception) { SlideshowSpeed.Default }
    }

    override val slideshowShuffle: Flow<Boolean> = dataStore.data.map {
        it[KEY_SLIDESHOW_SHUFFLE] ?: false
    }

    override val timelineZoom: Flow<TimelineZoom> = dataStore.data.map {
        val name = it[KEY_TIMELINE_ZOOM] ?: TimelineZoom.Default.name
        TimelineZoom.entries.find { z -> z.name == name } ?: TimelineZoom.Default
    }

    override val semanticSearch: Flow<Boolean> = dataStore.data.map {
        it[KEY_SEMANTIC_SEARCH] ?: true
    }

    override val albumSortOption: Flow<AlbumSortOption> = dataStore.data.map {
        val name = it[KEY_ALBUM_SORT] ?: AlbumSortOption.Recency.name
        try { AlbumSortOption.valueOf(name) } catch (e: Exception) { AlbumSortOption.Recency }
    }

    override val hiddenAlbums: Flow<Set<String>> = dataStore.data.map {
        it[KEY_HIDDEN_ALBUMS] ?: emptySet()
    }

    override suspend fun setThemeMode(mode: Int) {
        dataStore.edit { it[KEY_THEME_MODE] = mode }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    override suspend fun setAutoplayVideos(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTOPLAY_VIDEOS] = enabled }
    }

    override suspend fun setMuteVideos(enabled: Boolean) {
        dataStore.edit { it[KEY_MUTE_VIDEOS] = enabled }
    }

    override suspend fun setBackgroundScan(enabled: Boolean) {
        dataStore.edit { it[KEY_BACKGROUND_SCAN] = enabled }
    }

    override suspend fun setScanOnlyWhileCharging(enabled: Boolean) {
        dataStore.edit { it[KEY_SCAN_ONLY_CHARGING] = enabled }
    }

    override suspend fun setSlideshowSpeed(speed: SlideshowSpeed) {
        dataStore.edit { it[KEY_SLIDESHOW_SPEED] = speed.name }
    }

    override suspend fun setSlideshowShuffle(enabled: Boolean) {
        dataStore.edit { it[KEY_SLIDESHOW_SHUFFLE] = enabled }
    }

    override suspend fun setTimelineZoom(zoom: TimelineZoom) {
        dataStore.edit { it[KEY_TIMELINE_ZOOM] = zoom.name }
    }

    override suspend fun setSemanticSearch(enabled: Boolean) {
        dataStore.edit { it[KEY_SEMANTIC_SEARCH] = enabled }
    }

    override suspend fun setAlbumSortOption(option: AlbumSortOption) {
        dataStore.edit { it[KEY_ALBUM_SORT] = option.name }
    }

    override suspend fun setHiddenAlbums(albums: Set<String>) {
        dataStore.edit { it[KEY_HIDDEN_ALBUMS] = albums }
    }

    override fun getThemeModeSync(): Int = runBlocking {
        themeMode.first()
    }

    override fun getSemanticSearchSync(): Boolean = runBlocking {
        semanticSearch.first()
    }

    override fun getBackgroundScanSync(): Boolean = runBlocking {
        backgroundScan.first()
    }

    override fun getScanOnlyWhileChargingSync(): Boolean = runBlocking {
        scanOnlyWhileCharging.first()
    }

    override fun getTimelineZoomSync(): TimelineZoom = runBlocking {
        timelineZoom.first()
    }
}