package com.femi.gallery.presentation.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.femi.gallery.domain.mirror.AlbumSortOption
import com.femi.gallery.domain.model.SlideshowSpeed
import com.femi.gallery.presentation.pictures.TimelineZoom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gallery_settings",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "gallery_settings_prefs"))
    }
)

object ThemePreferences {
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

    fun getThemeMode(context: Context): Int = runBlocking {
        context.dataStore.data.map { it[KEY_THEME_MODE] ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM }.first()
    }

    fun setThemeMode(context: Context, mode: Int) = runBlocking {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun getAutoPlayVideos(context: Context): Boolean = runBlocking {
        context.dataStore.data.map { it[KEY_AUTOPLAY_VIDEOS] ?: true }.first()
    }

    fun setAutoPlayVideos(context: Context, enabled: Boolean) = runBlocking {
        context.dataStore.edit { it[KEY_AUTOPLAY_VIDEOS] = enabled }
    }

    fun getMuteVideos(context: Context): Boolean = runBlocking {
        context.dataStore.data.map { it[KEY_MUTE_VIDEOS] ?: true }.first()
    }

    fun setMuteVideos(context: Context, muted: Boolean) = runBlocking {
        context.dataStore.edit { it[KEY_MUTE_VIDEOS] = muted }
    }

    fun getBackgroundScan(context: Context): Boolean = runBlocking {
        context.dataStore.data.map { it[KEY_BACKGROUND_SCAN] ?: true }.first()
    }

    fun setBackgroundScan(context: Context, enabled: Boolean) = runBlocking {
        context.dataStore.edit { it[KEY_BACKGROUND_SCAN] = enabled }
    }

    fun getScanOnlyWhileCharging(context: Context): Boolean = runBlocking {
        context.dataStore.data.map { it[KEY_SCAN_ONLY_CHARGING] ?: false }.first()
    }

    fun setScanOnlyWhileCharging(context: Context, enabled: Boolean) = runBlocking {
        context.dataStore.edit { it[KEY_SCAN_ONLY_CHARGING] = enabled }
    }

    fun getSlideshowSpeed(context: Context): SlideshowSpeed = runBlocking {
        val name = context.dataStore.data.map { it[KEY_SLIDESHOW_SPEED] }.first()
        name?.let { runCatching { SlideshowSpeed.valueOf(it) }.getOrNull() } ?: SlideshowSpeed.Default
    }

    fun setSlideshowSpeed(context: Context, speed: SlideshowSpeed) = runBlocking {
        context.dataStore.edit { it[KEY_SLIDESHOW_SPEED] = speed.name }
    }

    fun getSlideshowShuffle(context: Context): Boolean = runBlocking {
        context.dataStore.data.map { it[KEY_SLIDESHOW_SHUFFLE] ?: false }.first()
    }

    fun setSlideshowShuffle(context: Context, shuffle: Boolean) = runBlocking {
        context.dataStore.edit { it[KEY_SLIDESHOW_SHUFFLE] = shuffle }
    }

    fun getTimelineZoom(context: Context): TimelineZoom = runBlocking {
        val name = context.dataStore.data.map { it[KEY_TIMELINE_ZOOM] }.first()
        name?.let { runCatching { TimelineZoom.valueOf(it) }.getOrNull() } ?: TimelineZoom.Default
    }

    fun setTimelineZoom(context: Context, zoom: TimelineZoom) = runBlocking {
        context.dataStore.edit { it[KEY_TIMELINE_ZOOM] = zoom.name }
    }

    fun getSemanticSearch(context: Context): Boolean = runBlocking {
        context.dataStore.data.map { it[KEY_SEMANTIC_SEARCH] ?: true }.first()
    }

    fun setSemanticSearch(context: Context, enabled: Boolean) = runBlocking {
        context.dataStore.edit { it[KEY_SEMANTIC_SEARCH] = enabled }
    }

    fun getAlbumSortOption(context: Context): AlbumSortOption = runBlocking {
        val name = context.dataStore.data.map { it[KEY_ALBUM_SORT] }.first()
        name?.let { runCatching { AlbumSortOption.valueOf(it) }.getOrNull() } ?: AlbumSortOption.Name
    }

    fun getAlbumSortOptionFlow(context: Context): Flow<AlbumSortOption> =
        context.dataStore.data.map { prefs ->
            val name = prefs[KEY_ALBUM_SORT]
            name?.let { runCatching { AlbumSortOption.valueOf(it) }.getOrNull() } ?: AlbumSortOption.Name
        }

    fun setAlbumSortOption(context: Context, option: AlbumSortOption) = runBlocking {
        context.dataStore.edit { it[KEY_ALBUM_SORT] = option.name }
    }

    fun getHiddenAlbums(context: Context): Set<Long> = runBlocking {
        val strings = context.dataStore.data.map { it[KEY_HIDDEN_ALBUMS] ?: emptySet() }.first()
        strings.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun getHiddenAlbumsFlow(context: Context): Flow<Set<Long>> =
        context.dataStore.data.map { prefs ->
            val strings = prefs[KEY_HIDDEN_ALBUMS] ?: emptySet()
            strings.mapNotNull { it.toLongOrNull() }.toSet()
        }

    fun setHiddenAlbums(context: Context, hiddenIds: Set<Long>) = runBlocking {
        context.dataStore.edit { it[KEY_HIDDEN_ALBUMS] = hiddenIds.map { id -> id.toString() }.toSet() }
    }
}
