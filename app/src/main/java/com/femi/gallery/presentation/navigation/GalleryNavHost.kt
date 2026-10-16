package com.femi.gallery.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.femi.gallery.presentation.albums.AlbumScreen
import com.femi.gallery.presentation.albums.AlbumViewModel
import com.femi.gallery.presentation.albums.AlbumsScreen
import com.femi.gallery.presentation.pictures.PicturesScreen
import com.femi.gallery.presentation.placeholder.PlaceholderScreen
import com.femi.gallery.presentation.search.SearchScreen
import com.femi.gallery.presentation.viewer.ViewerScreen
import com.femi.gallery.presentation.viewer.ViewerViewModel

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
        composable(GalleryDestination.Pictures.route) {
            PicturesScreen(
                onMediaClick = { mediaIndex -> navController.navigate(GalleryRoutes.viewer(mediaIndex)) },
                onSearchClick = { navController.navigate(GalleryRoutes.SEARCH) },
            )
        }
        composable(GalleryDestination.Albums.route) {
            AlbumsScreen(
                onAlbumClick = { bucketId -> navController.navigate(GalleryRoutes.album(bucketId)) },
            )
        }
        composable(
            route = GalleryRoutes.ALBUM,
            arguments = listOf(navArgument(AlbumViewModel.BUCKET_ARG) { type = NavType.LongType }),
        ) {
            AlbumScreen(
                onBack = { navController.popBackStack() },
                onMediaClick = { bucketId, mediaIndex ->
                    navController.navigate(GalleryRoutes.viewer(mediaIndex, bucketId))
                },
            )
        }
        composable(GalleryRoutes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onMediaClick = { mediaIndex, query ->
                    navController.navigate(GalleryRoutes.viewer(mediaIndex, query = query))
                },
            )
        }
        // Replaced by a real screen in a later patch.
        composable(GalleryDestination.Stories.route) {
            PlaceholderScreen(title = stringResource(GalleryDestination.Stories.label))
        }
        composable(
            route = GalleryRoutes.VIEWER,
            arguments = listOf(
                navArgument(ViewerViewModel.INDEX_ARG) { type = NavType.IntType },
                navArgument(ViewerViewModel.BUCKET_ARG) {
                    type = NavType.LongType
                    defaultValue = GalleryRoutes.NO_BUCKET
                },
                navArgument(ViewerViewModel.QUERY_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            ViewerScreen(onBack = { navController.popBackStack() })
        }
    }
}
