package com.femi.gallery.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.femi.gallery.R

/** Top-level destinations shown in the bottom bar, mirroring Samsung Gallery's main tabs. */
enum class GalleryDestination(
    val route: String,
    @param:StringRes val label: Int,
    val icon: ImageVector,
) {
    Pictures("pictures", R.string.nav_pictures, Icons.Outlined.PhotoLibrary),
    Albums("albums", R.string.nav_albums, Icons.Outlined.PhotoAlbum),
    Stories("stories", R.string.nav_stories, Icons.Outlined.AutoStories),
}
