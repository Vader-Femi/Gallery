package com.femi.gallery.presentation.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.femi.gallery.domain.model.SlideshowSpeed
import com.femi.gallery.presentation.pictures.TimelineZoom

object ThemePreferences {
    private const val PREF_NAME = "gallery_settings_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_AUTOPLAY_VIDEOS = "autoplay_videos"
    private const val KEY_MUTE_VIDEOS = "mute_videos"
    private const val KEY_BACKGROUND_SCAN = "background_scan"
    private const val KEY_SCAN_ONLY_CHARGING = "scan_only_while_charging"
    private const val KEY_SLIDESHOW_SPEED = "slideshow_speed"
    private const val KEY_SLIDESHOW_SHUFFLE = "slideshow_shuffle"
    private const val KEY_TIMELINE_ZOOM = "timeline_zoom"
    private const val KEY_SEMANTIC_SEARCH = "semantic_search"

    fun getThemeMode(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    fun setThemeMode(context: Context, mode: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putInt(KEY_THEME_MODE, mode) }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun getAutoPlayVideos(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTOPLAY_VIDEOS, true)
    }

    fun setAutoPlayVideos(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_AUTOPLAY_VIDEOS, enabled) }
    }

    fun getMuteVideos(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_MUTE_VIDEOS, true)
    }

    fun setMuteVideos(context: Context, muted: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_MUTE_VIDEOS, muted) }
    }

    /** Whether the library scans may carry on while the app is closed. On unless the person turned it off. */
    fun getBackgroundScan(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BACKGROUND_SCAN, true)
    }

    fun setBackgroundScan(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_BACKGROUND_SCAN, enabled) }
    }

    /** Whether background scans wait for the charger. Off unless the person chose it. */
    fun getScanOnlyWhileCharging(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SCAN_ONLY_CHARGING, false)
    }

    fun setScanOnlyWhileCharging(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_SCAN_ONLY_CHARGING, enabled) }
    }

    fun getSlideshowSpeed(context: Context): SlideshowSpeed {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_SLIDESHOW_SPEED, null)
        return name?.let { runCatching { SlideshowSpeed.valueOf(it) }.getOrNull() } ?: SlideshowSpeed.Default
    }

    fun setSlideshowSpeed(context: Context, speed: SlideshowSpeed) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_SLIDESHOW_SPEED, speed.name) }
    }

    fun getSlideshowShuffle(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SLIDESHOW_SHUFFLE, false)
    }

    fun setSlideshowShuffle(context: Context, shuffle: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_SLIDESHOW_SHUFFLE, shuffle) }
    }

    fun getTimelineZoom(context: Context): TimelineZoom {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_TIMELINE_ZOOM, null)
        return name?.let { runCatching { TimelineZoom.valueOf(it) }.getOrNull() } ?: TimelineZoom.Default
    }

    fun setTimelineZoom(context: Context, zoom: TimelineZoom) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_TIMELINE_ZOOM, zoom.name) }
    }

    /** Whether a search may also match photos by what they show (when a model is installed). On unless turned off. */
    fun getSemanticSearch(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SEMANTIC_SEARCH, true)
    }

    fun setSemanticSearch(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_SEMANTIC_SEARCH, enabled) }
    }
}
