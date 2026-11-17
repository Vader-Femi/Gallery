package com.femi.gallery.presentation.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * For a screen that draws its own black background (a slideshow, a video): the status and navigation bar icons must
 * be light there whatever the theme, or in the light theme they are dark on black. The theme's own choice is put back
 * when the screen goes away.
 */
@Composable
fun LightSystemBarIconsOnBlack() {
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    DisposableEffect(view, dark) {
        val controller = view.context.findActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = !dark
            controller?.isAppearanceLightNavigationBars = !dark
        }
    }
}
