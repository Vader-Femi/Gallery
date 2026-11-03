package com.femi.gallery.presentation.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gallery_settings",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "gallery_settings_prefs"))
    }
)
