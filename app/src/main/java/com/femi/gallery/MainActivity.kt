package com.femi.gallery

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.femi.gallery.presentation.GalleryApp
import com.femi.gallery.ui.theme.GalleryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
// FragmentActivity (still a ComponentActivity) because BiometricPrompt needs one.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GalleryTheme {
                GalleryApp()
            }
        }
    }
}
