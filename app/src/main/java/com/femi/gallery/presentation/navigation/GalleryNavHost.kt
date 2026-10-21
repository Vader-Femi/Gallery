package com.femi.gallery.presentation.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NamedNavArgument
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
import com.femi.gallery.presentation.common.LocalSharedTransitionScope
import com.femi.gallery.presentation.editor.EditorScreen
import com.femi.gallery.presentation.editor.EditorViewModel
import com.femi.gallery.presentation.pictures.PicturesScreen
import com.femi.gallery.presentation.map.MapScreen
import com.femi.gallery.presentation.place.PlaceScreen
import com.femi.gallery.presentation.place.PlaceViewModel
import com.femi.gallery.presentation.search.SearchScreen
import com.femi.gallery.presentation.stories.SlideshowScreen
import com.femi.gallery.presentation.stories.SlideshowViewModel
import com.femi.gallery.presentation.stories.StoriesScreen
import com.femi.gallery.presentation.stories.StoryScreen
import com.femi.gallery.presentation.stories.StoryViewModel
import com.femi.gallery.presentation.common.ViewedMedia
import com.femi.gallery.presentation.viewer.ViewerScreen
import com.femi.gallery.presentation.viewer.ViewerViewModel

/**
 * All screens of the app. The grid screens and the viewer share a [SharedTransitionLayout], so a
 * tapped thumbnail grows into the viewer and the viewer's photo shrinks back into the grid.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = GalleryDestination.Pictures.route,
                modifier = Modifier.fillMaxSize(),
            ) {
                sharedComposable(GalleryDestination.Pictures.route) { entry ->
                    PicturesScreen(
                        onMediaClick = { mediaIndex, mediaId -> navController.navigate(GalleryRoutes.viewer(mediaIndex, mediaId)) },
                        onSearchClick = { navController.navigate(GalleryRoutes.SEARCH) },
                        onMapClick = { navController.navigate(GalleryRoutes.MAP) },
                        viewed = entry.viewedMedia(),
                        onViewedConsumed = entry::clearViewedMediaIndex,
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
                            navController.navigate(GalleryRoutes.viewer(mediaIndex, mediaId, bucketId))
                        },
                        viewed = entry.viewedMedia(),
                        onViewedConsumed = entry::clearViewedMediaIndex,
                    )
                }
                sharedComposable(GalleryRoutes.SEARCH) { entry ->
                    SearchScreen(
                        onBack = { navController.popBackStack() },
                        onMediaClick = { mediaIndex, mediaId, query ->
                            navController.navigate(GalleryRoutes.viewer(mediaIndex, mediaId, query = query))
                        },
                        viewed = entry.viewedMedia(),
                        onViewedConsumed = entry::clearViewedMediaIndex,
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
                            navController.navigate(GalleryRoutes.viewer(mediaIndex, mediaId, query = query))
                        },
                        onPlaySlideshow = { query -> navController.navigate(GalleryRoutes.slideshow(query)) },
                        viewed = entry.viewedMedia(),
                        onViewedConsumed = entry::clearViewedMediaIndex,
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
                            navController.navigate(GalleryRoutes.viewer(mediaIndex, mediaId, query = query))
                        },
                        onPlaySlideshow = { query -> navController.navigate(GalleryRoutes.slideshow(query)) },
                        viewed = entry.viewedMedia(),
                        onViewedConsumed = entry::clearViewedMediaIndex,
                    )
                }
                composable(
                    route = GalleryRoutes.SLIDESHOW,
                    arguments = listOf(navArgument(SlideshowViewModel.QUERY_ARG) { type = NavType.StringType }),
                ) {
                    SlideshowScreen(onClose = { navController.popBackStack() })
                }
                sharedComposable(
                    route = GalleryRoutes.VIEWER,
                    arguments = listOf(
                        navArgument(ViewerViewModel.INDEX_ARG) { type = NavType.IntType },
                        navArgument(ViewerViewModel.MEDIA_ID_ARG) {
                            type = NavType.LongType
                            defaultValue = GalleryRoutes.NO_MEDIA_ID
                        },
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
                        // Tells the screen behind (the one that opened the viewer) which photo we ended on.
                        onViewedChange = { mediaIndex, mediaId ->
                            val handle = navController.previousBackStackEntry?.savedStateHandle
                            // The id first: the index is what tells the grid there is something to show.
                            handle?.set(GalleryRoutes.VIEWED_ID_KEY, mediaId)
                            handle?.set(GalleryRoutes.VIEWED_INDEX_KEY, mediaIndex)
                        },
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
    }
}

/** A destination that takes part in the grid-to-viewer shared transition. */
private fun NavGraphBuilder.sharedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable(route = route, arguments = arguments) { entry ->
        // `this` is the AnimatedContentScope of this destination, which the transition needs.
        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) { content(entry) }
    }
}

/** The photo the viewer last showed, or null when there is nothing to scroll to. */
@Composable
private fun NavBackStackEntry.viewedMedia(): ViewedMedia? {
    val index by savedStateHandle.getStateFlow(GalleryRoutes.VIEWED_INDEX_KEY, -1).collectAsStateWithLifecycle()
    val id by savedStateHandle
        .getStateFlow(GalleryRoutes.VIEWED_ID_KEY, GalleryRoutes.NO_MEDIA_ID)
        .collectAsStateWithLifecycle()
    return if (index >= 0) ViewedMedia(index, id) else null
}

private fun NavBackStackEntry.clearViewedMediaIndex() {
    savedStateHandle[GalleryRoutes.VIEWED_INDEX_KEY] = -1
    savedStateHandle[GalleryRoutes.VIEWED_ID_KEY] = GalleryRoutes.NO_MEDIA_ID
}
