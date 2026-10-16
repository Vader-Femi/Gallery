package com.femi.gallery.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** App-level UI state that screens can change, e.g. hiding the tab bar during selection. */
@Stable
class ChromeState {
    var bottomBarHidden by mutableStateOf(false)
}

val LocalChromeState = staticCompositionLocalOf { ChromeState() }

/** Hides the bottom tab bar for as long as [hidden] is true and this composable is on screen. */
@Composable
fun HideBottomBarWhile(hidden: Boolean) {
    val chrome = LocalChromeState.current
    DisposableEffect(hidden) {
        chrome.bottomBarHidden = hidden
        onDispose { chrome.bottomBarHidden = false }
    }
}
