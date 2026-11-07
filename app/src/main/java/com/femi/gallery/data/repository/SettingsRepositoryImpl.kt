package com.femi.gallery.data.repository

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.femi.gallery.di.ApplicationScope
import com.femi.gallery.domain.clip.ClipNormalization
import com.femi.gallery.domain.mirror.AlbumSortOption
import com.femi.gallery.domain.model.SlideshowMusicTrack
import com.femi.gallery.domain.model.SlideshowSpeed
import com.femi.gallery.domain.repository.SemanticSearchStrictness
import com.femi.gallery.domain.repository.SettingsRepository
import com.femi.gallery.presentation.pictures.TimelineZoom
import com.femi.gallery.presentation.settings.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
) : SettingsRepository {

    constructor(context: Context) : this(
        context = context,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private val dataStore = context.dataStore

    companion object {
        private val KEY_THEME_MODE = intPreferencesKey("theme_mode")
        private val KEY_AUTOPLAY_VIDEOS = booleanPreferencesKey("autoplay_videos")
        private val KEY_MUTE_VIDEOS = booleanPreferencesKey("mute_videos")
        private val KEY_PLAY_AUDIO_IN_BACKGROUND = booleanPreferencesKey("play_audio_in_background")
        private val KEY_BACKGROUND_SCAN = booleanPreferencesKey("background_scan")
        private val KEY_SCAN_ONLY_CHARGING = booleanPreferencesKey("scan_only_while_charging")
        private val KEY_SLIDESHOW_SPEED = stringPreferencesKey("slideshow_speed")
        private val KEY_SLIDESHOW_SHUFFLE = booleanPreferencesKey("slideshow_shuffle")
        private val KEY_SLIDESHOW_MUSIC_ENABLED = booleanPreferencesKey("slideshow_music_enabled")
        private val KEY_SLIDESHOW_MUSIC_TRACK = stringPreferencesKey("slideshow_music_track")
        private val KEY_SLIDESHOW_CUSTOM_MUSIC_URI = stringPreferencesKey("slideshow_custom_music_uri")
        private val KEY_SLIDESHOW_CUSTOM_MUSIC_NAME = stringPreferencesKey("slideshow_custom_music_name")
        private val KEY_TIMELINE_ZOOM = stringPreferencesKey("timeline_zoom")
        private val KEY_SEMANTIC_SEARCH = booleanPreferencesKey("semantic_search")
        private val KEY_FACE_RECOGNITION = booleanPreferencesKey("face_recognition")
        private val KEY_SEMANTIC_SEARCH_STRICTNESS = stringPreferencesKey("semantic_search_strictness")
        private val KEY_CLIP_NORMALIZATION = stringPreferencesKey("clip_normalization")
        private val KEY_ALBUM_SORT = stringPreferencesKey("album_sort_option")
        private val KEY_HIDDEN_ALBUMS = stringSetPreferencesKey("hidden_albums")

        // Process-wide in-memory cache to eliminate runBlocking calls across components.
        @Volatile private var cachedThemeMode: Int? = null
        @Volatile private var cachedSemanticSearch: Boolean? = null
        @Volatile private var cachedBackgroundScan: Boolean? = null
        @Volatile private var cachedScanOnlyWhileCharging: Boolean? = null
        @Volatile private var cachedPlayAudioInBackground: Boolean? = null
        @Volatile private var cachedTimelineZoom: TimelineZoom? = null
        @Volatile private var cachedClipNormalization: ClipNormalization? = null
    }

    init {
        scope.launch {
            dataStore.data.collect { prefs ->
                updateCache(prefs)
            }
        }
    }

    private fun updateCache(prefs: Preferences) {
        cachedThemeMode = prefs[KEY_THEME_MODE] ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        cachedSemanticSearch = prefs[KEY_SEMANTIC_SEARCH] ?: true
        cachedBackgroundScan = prefs[KEY_BACKGROUND_SCAN] ?: true
        cachedScanOnlyWhileCharging = prefs[KEY_SCAN_ONLY_CHARGING] ?: false
        cachedPlayAudioInBackground = prefs[KEY_PLAY_AUDIO_IN_BACKGROUND] ?: false

        val zoomName = prefs[KEY_TIMELINE_ZOOM] ?: TimelineZoom.Default.name
        cachedTimelineZoom = TimelineZoom.entries.find { it.name == zoomName } ?: TimelineZoom.Default

        val clipNormName = prefs[KEY_CLIP_NORMALIZATION] ?: ClipNormalization.OpenAiClip.name
        cachedClipNormalization = try { ClipNormalization.valueOf(clipNormName) } catch (_: Exception) { ClipNormalization.OpenAiClip }
    }

    override val themeMode: Flow<Int> = dataStore.data.map {
        it[KEY_THEME_MODE] ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }.distinctUntilChanged()

    override val autoplayVideos: Flow<Boolean> = dataStore.data.map {
        it[KEY_AUTOPLAY_VIDEOS] ?: true
    }.distinctUntilChanged()

    override val muteVideos: Flow<Boolean> = dataStore.data.map {
        it[KEY_MUTE_VIDEOS] ?: true
    }.distinctUntilChanged()

    override val playAudioInBackground: Flow<Boolean> = dataStore.data.map {
        it[KEY_PLAY_AUDIO_IN_BACKGROUND] ?: false
    }.distinctUntilChanged()

    override val backgroundScan: Flow<Boolean> = dataStore.data.map {
        it[KEY_BACKGROUND_SCAN] ?: true
    }.distinctUntilChanged()

    override val scanOnlyWhileCharging: Flow<Boolean> = dataStore.data.map {
        it[KEY_SCAN_ONLY_CHARGING] ?: false
    }.distinctUntilChanged()

    override val slideshowSpeed: Flow<SlideshowSpeed> = dataStore.data.map {
        val name = it[KEY_SLIDESHOW_SPEED] ?: SlideshowSpeed.Default.name
        try { SlideshowSpeed.valueOf(name) } catch (_: Exception) { SlideshowSpeed.Default }
    }.distinctUntilChanged()

    override val slideshowShuffle: Flow<Boolean> = dataStore.data.map {
        it[KEY_SLIDESHOW_SHUFFLE] ?: false
    }.distinctUntilChanged()

    override val slideshowMusicEnabled: Flow<Boolean> = dataStore.data.map {
        it[KEY_SLIDESHOW_MUSIC_ENABLED] ?: true
    }.distinctUntilChanged()

    override val slideshowMusicTrack: Flow<SlideshowMusicTrack> = dataStore.data.map {
        val name = it[KEY_SLIDESHOW_MUSIC_TRACK] ?: SlideshowMusicTrack.Default.name
        try { SlideshowMusicTrack.valueOf(name) } catch (_: Exception) { SlideshowMusicTrack.Default }
    }.distinctUntilChanged()

    override val slideshowCustomMusicUri: Flow<String?> = dataStore.data.map {
        it[KEY_SLIDESHOW_CUSTOM_MUSIC_URI]
    }.distinctUntilChanged()

    override val slideshowCustomMusicName: Flow<String?> = dataStore.data.map {
        it[KEY_SLIDESHOW_CUSTOM_MUSIC_NAME]
    }.distinctUntilChanged()

    override val timelineZoom: Flow<TimelineZoom> = dataStore.data.map {
        val name = it[KEY_TIMELINE_ZOOM] ?: TimelineZoom.Default.name
        TimelineZoom.entries.find { z -> z.name == name } ?: TimelineZoom.Default
    }.distinctUntilChanged()

    override val semanticSearch: Flow<Boolean> = dataStore.data.map {
        it[KEY_SEMANTIC_SEARCH] ?: true
    }.distinctUntilChanged()

    override val faceRecognition: Flow<Boolean> = dataStore.data.map {
        it[KEY_FACE_RECOGNITION] ?: true
    }.distinctUntilChanged()

    override val semanticSearchStrictness: Flow<SemanticSearchStrictness> = dataStore.data.map {
        val name = it[KEY_SEMANTIC_SEARCH_STRICTNESS] ?: SemanticSearchStrictness.Normal.name
        try { SemanticSearchStrictness.valueOf(name) } catch (_: Exception) { SemanticSearchStrictness.Normal }
    }.distinctUntilChanged()

    override val clipNormalization: Flow<ClipNormalization> = dataStore.data.map {
        val name = it[KEY_CLIP_NORMALIZATION] ?: ClipNormalization.OpenAiClip.name
        try { ClipNormalization.valueOf(name) } catch (_: Exception) { ClipNormalization.OpenAiClip }
    }.distinctUntilChanged()

    override val albumSortOption: Flow<AlbumSortOption> = dataStore.data.map {
        val name = it[KEY_ALBUM_SORT] ?: AlbumSortOption.Name.name
        try { AlbumSortOption.valueOf(name) } catch (_: Exception) { AlbumSortOption.Name }
    }.distinctUntilChanged()

    override val hiddenAlbums: Flow<Set<String>> = dataStore.data.map {
        it[KEY_HIDDEN_ALBUMS] ?: emptySet()
    }.distinctUntilChanged()

    override suspend fun setThemeMode(mode: Int) {
        cachedThemeMode = mode
        dataStore.edit { it[KEY_THEME_MODE] = mode }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    override suspend fun setAutoplayVideos(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTOPLAY_VIDEOS] = enabled }
    }

    override suspend fun setMuteVideos(enabled: Boolean) {
        dataStore.edit { it[KEY_MUTE_VIDEOS] = enabled }
    }

    override suspend fun setPlayAudioInBackground(enabled: Boolean) {
        cachedPlayAudioInBackground = enabled
        dataStore.edit { it[KEY_PLAY_AUDIO_IN_BACKGROUND] = enabled }
    }

    override suspend fun setBackgroundScan(enabled: Boolean) {
        cachedBackgroundScan = enabled
        dataStore.edit { it[KEY_BACKGROUND_SCAN] = enabled }
    }

    override suspend fun setScanOnlyWhileCharging(enabled: Boolean) {
        cachedScanOnlyWhileCharging = enabled
        dataStore.edit { it[KEY_SCAN_ONLY_CHARGING] = enabled }
    }

    override suspend fun setSlideshowSpeed(speed: SlideshowSpeed) {
        dataStore.edit { it[KEY_SLIDESHOW_SPEED] = speed.name }
    }

    override suspend fun setSlideshowShuffle(enabled: Boolean) {
        dataStore.edit { it[KEY_SLIDESHOW_SHUFFLE] = enabled }
    }

    override suspend fun setSlideshowMusicEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SLIDESHOW_MUSIC_ENABLED] = enabled }
    }

    override suspend fun setSlideshowMusicTrack(track: SlideshowMusicTrack) {
        dataStore.edit { it[KEY_SLIDESHOW_MUSIC_TRACK] = track.name }
    }

    override suspend fun setSlideshowCustomMusic(uri: String?, name: String?) {
        dataStore.edit {
            if (uri != null) it[KEY_SLIDESHOW_CUSTOM_MUSIC_URI] = uri else it.remove(KEY_SLIDESHOW_CUSTOM_MUSIC_URI)
            if (name != null) it[KEY_SLIDESHOW_CUSTOM_MUSIC_NAME] = name else it.remove(KEY_SLIDESHOW_CUSTOM_MUSIC_NAME)
        }
    }

    override suspend fun setTimelineZoom(zoom: TimelineZoom) {
        cachedTimelineZoom = zoom
        dataStore.edit { it[KEY_TIMELINE_ZOOM] = zoom.name }
    }

    override suspend fun setSemanticSearch(enabled: Boolean) {
        cachedSemanticSearch = enabled
        dataStore.edit { it[KEY_SEMANTIC_SEARCH] = enabled }
    }

    override suspend fun setFaceRecognition(enabled: Boolean) {
        dataStore.edit { it[KEY_FACE_RECOGNITION] = enabled }
    }

    override suspend fun setSemanticSearchStrictness(strictness: SemanticSearchStrictness) {
        dataStore.edit { it[KEY_SEMANTIC_SEARCH_STRICTNESS] = strictness.name }
    }

    override suspend fun setClipNormalization(normalization: ClipNormalization) {
        cachedClipNormalization = normalization
        dataStore.edit { it[KEY_CLIP_NORMALIZATION] = normalization.name }
    }

    override suspend fun setAlbumSortOption(option: AlbumSortOption) {
        dataStore.edit { it[KEY_ALBUM_SORT] = option.name }
    }

    override suspend fun setHiddenAlbums(albums: Set<String>) {
        dataStore.edit { it[KEY_HIDDEN_ALBUMS] = albums }
    }

    override fun getThemeModeSync(): Int =
        cachedThemeMode ?: runBlocking {
            val mode = dataStore.data.first()[KEY_THEME_MODE] ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            cachedThemeMode = mode
            mode
        }

    override fun getSemanticSearchSync(): Boolean =
        cachedSemanticSearch ?: runBlocking {
            val enabled = dataStore.data.first()[KEY_SEMANTIC_SEARCH] ?: true
            cachedSemanticSearch = enabled
            enabled
        }

    override fun getBackgroundScanSync(): Boolean =
        cachedBackgroundScan ?: runBlocking {
            val enabled = dataStore.data.first()[KEY_BACKGROUND_SCAN] ?: true
            cachedBackgroundScan = enabled
            enabled
        }

    override fun getScanOnlyWhileChargingSync(): Boolean =
        cachedScanOnlyWhileCharging ?: runBlocking {
            val charging = dataStore.data.first()[KEY_SCAN_ONLY_CHARGING] ?: false
            cachedScanOnlyWhileCharging = charging
            charging
        }

    override fun getTimelineZoomSync(): TimelineZoom =
        cachedTimelineZoom ?: runBlocking {
            val name = dataStore.data.first()[KEY_TIMELINE_ZOOM] ?: TimelineZoom.Default.name
            val zoom = TimelineZoom.entries.find { z -> z.name == name } ?: TimelineZoom.Default
            cachedTimelineZoom = zoom
            zoom
        }

    override fun getClipNormalizationSync(): ClipNormalization =
        cachedClipNormalization ?: runBlocking {
            val name = dataStore.data.first()[KEY_CLIP_NORMALIZATION] ?: ClipNormalization.OpenAiClip.name
            val norm = try { ClipNormalization.valueOf(name) } catch (_: Exception) { ClipNormalization.OpenAiClip }
            cachedClipNormalization = norm
            norm
        }

    override fun getPlayAudioInBackgroundSync(): Boolean =
        cachedPlayAudioInBackground ?: runBlocking {
            val enabled = dataStore.data.first()[KEY_PLAY_AUDIO_IN_BACKGROUND] ?: false
            cachedPlayAudioInBackground = enabled
            enabled
        }
}
