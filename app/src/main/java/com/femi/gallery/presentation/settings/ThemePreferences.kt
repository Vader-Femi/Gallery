package com.femi.gallery.presentation.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

object ThemePreferences {
    private const val PREF_NAME = "gallery_settings_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_AUTOPLAY_VIDEOS = "autoplay_videos"
    private const val KEY_MUTE_VIDEOS = "mute_videos"
    private const val KEY_BACKGROUND_SCAN = "background_scan"
    private const val KEY_SCAN_ONLY_CHARGING = "scan_only_while_charging"

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
}
