package com.femi.gallery

import android.app.Application
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
        AppCompatDelegate.setDefaultNightMode(ThemePreferences.getThemeMode(this))
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(MediaThumbnailFetcher.Factory()) }
            .build()
}
