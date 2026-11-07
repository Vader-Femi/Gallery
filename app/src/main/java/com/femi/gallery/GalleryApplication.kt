package com.femi.gallery

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import com.femi.gallery.core.lifecycle.AppForeground
import com.femi.gallery.data.image.MediaThumbnailFetcher
import com.femi.gallery.data.repository.SettingsRepositoryImpl
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GalleryApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        AppForeground.register(this)
        // The theme must be set before the first screen, so this one settings read blocks the main thread; measured
        // on a release build it takes about 11 to 14 ms the first time and 1 ms after that.
        val themeMode = SettingsRepositoryImpl(this).getThemeModeSync()
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(MediaThumbnailFetcher.Factory()) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .build()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            SingletonImageLoader.get(this).memoryCache?.clear()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        SingletonImageLoader.get(this).memoryCache?.clear()
    }
}
