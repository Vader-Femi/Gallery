package com.femi.gallery.presentation.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    SharedTransitionLayout(modifier = modifier) {
        NavHost(
            navController = navController,
            startDestination = GalleryDestination.Pictures.route,
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(220)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(220)) },
        ) {
            composable(GalleryDestination.Pictures.route) { backStackEntry ->
                val lastViewedMediaIndex by backStackEntry.savedStateHandle
                    .getStateFlow<Int?>("last_viewed_media_index", null)
                    .collectAsStateWithLifecycle()
                PicturesScreen(
                    onMediaClick = { mediaIndex -> navController.navigate(GalleryRoutes.viewer(mediaIndex)) },
                    onSearchClick = { navController.navigate(GalleryRoutes.SEARCH) },
                    lastViewedMediaIndex = lastViewedMediaIndex,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
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
            ) { backStackEntry ->
                val lastViewedMediaIndex by backStackEntry.savedStateHandle
                    .getStateFlow<Int?>("last_viewed_media_index", null)
                    .collectAsStateWithLifecycle()
                AlbumScreen(
                    onBack = { navController.popBackStack() },
                    onMediaClick = { bucketId, mediaIndex ->
                        navController.navigate(GalleryRoutes.viewer(mediaIndex, bucketId))
                    },
                    lastViewedMediaIndex = lastViewedMediaIndex,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
                )
            }
            composable(GalleryRoutes.SEARCH) { backStackEntry ->
                val lastViewedMediaIndex by backStackEntry.savedStateHandle
                    .getStateFlow<Int?>("last_viewed_media_index", null)
                    .collectAsStateWithLifecycle()
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onMediaClick = { mediaIndex, query ->
                        navController.navigate(GalleryRoutes.viewer(mediaIndex, query = query))
                    },
                    lastViewedMediaIndex = lastViewedMediaIndex,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
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
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(220)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(220)) },
            ) {
                val handlePageChange: (Int) -> Unit = remember {
                    { page -> navController.previousBackStackEntry?.savedStateHandle?.set("last_viewed_media_index", page) }
                }
                ViewerScreen(
                    onBack = { navController.popBackStack() },
                    onPageChange = handlePageChange,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this@composable,
                )
            }
        }
    }
}
