package com.femi.gallery.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.femi.gallery.presentation.albums.AlbumScreen
import com.femi.gallery.presentation.albums.AlbumViewModel
import com.femi.gallery.presentation.albums.AlbumsScreen
import com.femi.gallery.presentation.editor.EditorScreen
import com.femi.gallery.presentation.editor.EditorViewModel
import com.femi.gallery.presentation.pictures.PicturesScreen
import com.femi.gallery.presentation.search.SearchScreen
import com.femi.gallery.presentation.stories.SlideshowScreen
import com.femi.gallery.presentation.stories.SlideshowViewModel
import com.femi.gallery.presentation.stories.StoriesScreen
import com.femi.gallery.presentation.stories.StoryScreen
import com.femi.gallery.presentation.stories.StoryViewModel
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
        composable(GalleryDestination.Stories.route) {
            StoriesScreen(
                onStoryClick = { fromDay, toDay -> navController.navigate(GalleryRoutes.story(fromDay, toDay)) },
            )
        }
        composable(
            route = GalleryRoutes.STORY,
            arguments = listOf(
                navArgument(StoryViewModel.FROM_ARG) { type = NavType.LongType },
                navArgument(StoryViewModel.TO_ARG) { type = NavType.LongType },
            ),
        ) {
            StoryScreen(
                onBack = { navController.popBackStack() },
                onMediaClick = { mediaIndex, query ->
                    navController.navigate(GalleryRoutes.viewer(mediaIndex, query = query))
                },
                onPlaySlideshow = { query -> navController.navigate(GalleryRoutes.slideshow(query)) },
            )
        }
        composable(
            route = GalleryRoutes.SLIDESHOW,
            arguments = listOf(navArgument(SlideshowViewModel.QUERY_ARG) { type = NavType.StringType }),
        ) {
            SlideshowScreen(onClose = { navController.popBackStack() })
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
            ViewerScreen(
                onBack = { navController.popBackStack() },
                onEdit = { uri -> navController.navigate(GalleryRoutes.editor(uri)) },
            )
        }
        composable(
            route = GalleryRoutes.EDITOR,
            arguments = listOf(navArgument(EditorViewModel.URI_ARG) { type = NavType.StringType }),
        ) {
            EditorScreen(onClose = { navController.popBackStack() })
        }
    }
}
