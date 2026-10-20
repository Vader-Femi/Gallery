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
import com.femi.gallery.presentation.search.SearchScreen
import com.femi.gallery.presentation.stories.SlideshowScreen
import com.femi.gallery.presentation.stories.SlideshowViewModel
import com.femi.gallery.presentation.stories.StoriesScreen
import com.femi.gallery.presentation.stories.StoryScreen
import com.femi.gallery.presentation.stories.StoryViewModel
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
                        onMediaClick = { mediaIndex -> navController.navigate(GalleryRoutes.viewer(mediaIndex)) },
                        onSearchClick = { navController.navigate(GalleryRoutes.SEARCH) },
                        viewedMediaIndex = entry.viewedMediaIndex(),
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
                        onMediaClick = { bucketId, mediaIndex ->
                            navController.navigate(GalleryRoutes.viewer(mediaIndex, bucketId))
                        },
                        viewedMediaIndex = entry.viewedMediaIndex(),
                        onViewedConsumed = entry::clearViewedMediaIndex,
                    )
                }
                sharedComposable(GalleryRoutes.SEARCH) { entry ->
                    SearchScreen(
                        onBack = { navController.popBackStack() },
                        onMediaClick = { mediaIndex, query ->
                            navController.navigate(GalleryRoutes.viewer(mediaIndex, query = query))
                        },
                        viewedMediaIndex = entry.viewedMediaIndex(),
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
                        onMediaClick = { mediaIndex, query ->
                            navController.navigate(GalleryRoutes.viewer(mediaIndex, query = query))
                        },
                        onPlaySlideshow = { query -> navController.navigate(GalleryRoutes.slideshow(query)) },
                        viewedMediaIndex = entry.viewedMediaIndex(),
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
                        onViewedIndexChange = { mediaIndex ->
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(GalleryRoutes.VIEWED_INDEX_KEY, mediaIndex)
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

/** The photo the viewer last showed, or -1 when there is nothing to scroll to. */
@Composable
private fun NavBackStackEntry.viewedMediaIndex(): Int {
    val index by savedStateHandle.getStateFlow(GalleryRoutes.VIEWED_INDEX_KEY, -1).collectAsStateWithLifecycle()
    return index
}

private fun NavBackStackEntry.clearViewedMediaIndex() {
    savedStateHandle[GalleryRoutes.VIEWED_INDEX_KEY] = -1
}
