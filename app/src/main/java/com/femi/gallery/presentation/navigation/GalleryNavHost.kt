package com.femi.gallery.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.femi.gallery.presentation.placeholder.PlaceholderScreen

@Composable
fun GalleryNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = GalleryDestination.Pictures.route,
        modifier = modifier,
    ) {
        GalleryDestination.entries.forEach { destination ->
            composable(destination.route) {
                // Replaced by real screens in later patches.
                PlaceholderScreen(title = stringResource(destination.label))
            }
        }
    }
}
