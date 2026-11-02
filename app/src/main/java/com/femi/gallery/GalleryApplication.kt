package com.femi.gallery

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.femi.gallery.core.lifecycle.AppForeground
import com.femi.gallery.data.image.MediaThumbnailFetcher
import com.femi.gallery.presentation.settings.ThemePreferences
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GalleryApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        AppForeground.register(this)
        // The one settings read the app cannot do without: the theme must be set before the first screen. It blocks
        // this thread, so how long the first (opens the store and imports the old preferences) and the next one
        // (answered from memory) take is logged, to see on a release build whether a Flow-based version is worth it.
        val started = SystemClock.elapsedRealtime()
        val themeMode = ThemePreferences.getThemeMode(this)
        val first = SystemClock.elapsedRealtime() - started
        ThemePreferences.getAutoPlayVideos(this)
        val second = SystemClock.elapsedRealtime() - started - first
        Log.i("Settings", "First settings read on the main thread: $first ms, the next one: $second ms")
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(MediaThumbnailFetcher.Factory()) }
            .build()
}
