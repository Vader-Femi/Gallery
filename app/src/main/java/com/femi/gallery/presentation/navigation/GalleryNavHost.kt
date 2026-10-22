package com.femi.gallery.presentation.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.femi.gallery.presentation.albums.AlbumScreen
import com.femi.gallery.presentation.albums.AlbumViewModel
import com.femi.gallery.presentation.albums.AlbumsScreen
import com.femi.gallery.presentation.common.LocalAnimatedVisibilityScope
import com.femi.gallery.presentation.editor.EditorScreen
import com.femi.gallery.presentation.editor.EditorViewModel
import com.femi.gallery.presentation.pictures.PicturesScreen
import com.femi.gallery.presentation.map.MapScreen
import com.femi.gallery.presentation.place.PlaceScreen
import com.femi.gallery.presentation.place.PlaceViewModel
import com.femi.gallery.presentation.search.SearchScreen
import com.femi.gallery.presentation.viewer.ViewerSession
import com.femi.gallery.presentation.stories.SlideshowScreen
import com.femi.gallery.presentation.stories.SlideshowViewModel
import com.femi.gallery.presentation.stories.StoriesScreen
import com.femi.gallery.presentation.stories.StoryScreen
import com.femi.gallery.presentation.stories.StoryViewModel
import com.femi.gallery.core.perf.ScreenTag

/**
 * All screens of the app. The grid screens and the viewer share a [SharedTransitionLayout], so a
 * tapped thumbnail grows into the viewer and the viewer's photo shrinks back into the grid.
 */
internal const val EnterFadeMillis = 180
internal const val ExitFadeMillis = 120

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryNavHost(
    navController: NavHostController,
    viewerSession: ViewerSession,
    modifier: Modifier = Modifier,
) {
    // Tells the frame monitor which screen is showing, so a slow frame can be blamed on it.
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            ScreenTag.base = destination.route.orEmpty().substringBefore('/').substringBefore('?')
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    NavHost(
        navController = navController,
        startDestination = GalleryDestination.Pictures.route,
        modifier = modifier.fillMaxSize(),
        // The library default is a 700 ms fade, which leaves the old screen showing for most of a
        // second. Shared photos move on their own; the screens only need to get out of the way.
        enterTransition = { fadeIn(tween(EnterFadeMillis)) },
        exitTransition = { fadeOut(tween(ExitFadeMillis)) },
        popEnterTransition = { fadeIn(tween(EnterFadeMillis)) },
        popExitTransition = { fadeOut(tween(ExitFadeMillis)) },
    ) {
        sharedComposable(GalleryDestination.Pictures.route) { entry ->
            PicturesScreen(
                onMediaClick = { mediaIndex, mediaId ->
                    viewerSession.open(GalleryRoutes.viewer(mediaIndex, mediaId), origin = entry.id)
                },
                onSearchClick = { navController.navigate(GalleryRoutes.SEARCH) },
                onMapClick = { navController.navigate(GalleryRoutes.MAP) },
                viewed = viewerSession.viewedFor(entry.id),
                onViewedConsumed = viewerSession::consumeViewed,
            )
        }
        composable(GalleryDestination.Albums.route) {
            AlbumsScreen(
                onAlbumClick = { bucketId -> navController.navigate(GalleryRoutes.album(bucketId)) },
            )
        }
        sharedComposable(
            route = GalleryRoutes.ALBUM,
            arguments = listOf(navArgument(AlbumViewModel.BUCKET_ARG) { type = NavType.LongType }),
        ) { entry ->
            AlbumScreen(
                onBack = { navController.popBackStack() },
                onMediaClick = { bucketId, mediaIndex, mediaId ->
                    viewerSession.open(GalleryRoutes.viewer(mediaIndex, mediaId, bucketId), origin = entry.id)
                },
                viewed = viewerSession.viewedFor(entry.id),
                onViewedConsumed = viewerSession::consumeViewed,
            )
        }
        sharedComposable(GalleryRoutes.SEARCH) { entry ->
            SearchScreen(
                onBack = { navController.popBackStack() },
                onMediaClick = { mediaIndex, mediaId, query ->
                    viewerSession.open(GalleryRoutes.viewer(mediaIndex, mediaId, query = query), origin = entry.id)
                },
                viewed = viewerSession.viewedFor(entry.id),
                onViewedConsumed = viewerSession::consumeViewed,
            )
        }
        composable(GalleryDestination.Stories.route) {
            StoriesScreen(
                onStoryClick = { fromDay, toDay -> navController.navigate(GalleryRoutes.story(fromDay, toDay)) },
            )
        }
        sharedComposable(
            route = GalleryRoutes.STORY,
            arguments = listOf(
                navArgument(StoryViewModel.FROM_ARG) { type = NavType.LongType },
                navArgument(StoryViewModel.TO_ARG) { type = NavType.LongType },
            ),
        ) { entry ->
            StoryScreen(
                onBack = { navController.popBackStack() },
                onMediaClick = { mediaIndex, mediaId, query ->
                    viewerSession.open(GalleryRoutes.viewer(mediaIndex, mediaId, query = query), origin = entry.id)
                },
                onPlaySlideshow = { query -> navController.navigate(GalleryRoutes.slideshow(query)) },
                viewed = viewerSession.viewedFor(entry.id),
                onViewedConsumed = viewerSession::consumeViewed,
            )
        }
        composable(GalleryRoutes.MAP) {
            MapScreen(
                onBack = { navController.popBackStack() },
                onOpenCluster = { cluster ->
                    val query = cluster.box.toQuery()
                    if (cluster.count == 1) {
                        // One photo: straight to the viewer, which then holds just that photo.
                        navController.navigate(GalleryRoutes.viewer(0, cluster.coverId, query = query))
                    } else {
                        navController.navigate(GalleryRoutes.place(query))
                    }
                },
            )
        }
        sharedComposable(
            route = GalleryRoutes.PLACE,
            arguments = listOf(navArgument(PlaceViewModel.QUERY_ARG) { type = NavType.StringType }),
        ) { entry ->
            PlaceScreen(
                onBack = { navController.popBackStack() },
                onMediaClick = { mediaIndex, mediaId, query ->
                    viewerSession.open(GalleryRoutes.viewer(mediaIndex, mediaId, query = query), origin = entry.id)
                },
                onPlaySlideshow = { query -> navController.navigate(GalleryRoutes.slideshow(query)) },
                viewed = viewerSession.viewedFor(entry.id),
                onViewedConsumed = viewerSession::consumeViewed,
            )
        }
        composable(
            route = GalleryRoutes.SLIDESHOW,
            arguments = listOf(navArgument(SlideshowViewModel.QUERY_ARG) { type = NavType.StringType }),
        ) {
            SlideshowScreen(onClose = { navController.popBackStack() })
        }
        composable(
            route = GalleryRoutes.EDITOR,
            arguments = listOf(navArgument(EditorViewModel.URI_ARG) { type = NavType.StringType }),
        ) {
            EditorScreen(onClose = { navController.popBackStack() })
        }
    }
}

/** A destination that takes part in the grid-to-viewer shared transition. */
internal fun NavGraphBuilder.sharedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(route = route, arguments = arguments) { entry ->
        // `this` is the AnimatedContentScope of this destination, which the transition needs.
        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) { content(entry) }
    }
}
