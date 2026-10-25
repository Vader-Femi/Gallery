package com.femi.gallery.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.femi.gallery.R

/** Top-level destinations shown in the bottom bar, mirroring Samsung Gallery's main tabs. */
enum class GalleryDestination(
    val route: String,
    @param:StringRes val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Pictures("pictures", R.string.nav_pictures, Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    Albums("albums", R.string.nav_albums, Icons.Filled.PhotoAlbum, Icons.Outlined.PhotoAlbum),
    Menu("menu", R.string.nav_menu, Icons.Filled.Menu, Icons.Outlined.Menu),
}
