package com.femi.gallery.presentation.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.femi.gallery.core.perf.ScreenTag
import com.femi.gallery.domain.model.SpecialAlbums
import com.femi.gallery.presentation.albums.AlbumScreen
import com.femi.gallery.presentation.albums.AlbumsScreen
import com.femi.gallery.presentation.common.LocalAnimatedVisibilityScope
import com.femi.gallery.presentation.duplicates.DuplicatesScreen
import com.femi.gallery.presentation.duplicates.SimilarPhotosScreen
import com.femi.gallery.presentation.editor.EditorScreen
import com.femi.gallery.presentation.images.ImagesScreen
import com.femi.gallery.presentation.map.MapScreen
import com.femi.gallery.presentation.people.PeopleScreen
import com.femi.gallery.presentation.people.PersonScreen
import com.femi.gallery.presentation.pictures.PicturesScreen
import com.femi.gallery.presentation.place.PlaceScreen
import com.femi.gallery.presentation.search.SearchScreen
import com.femi.gallery.presentation.settings.SettingsScreen
import com.femi.gallery.presentation.stories.SlideshowScreen
import com.femi.gallery.presentation.stories.StoriesScreen
import com.femi.gallery.presentation.stories.StoryScreen
import com.femi.gallery.presentation.trim.TrimScreen
import com.femi.gallery.presentation.videos.VideosScreen
import com.femi.gallery.presentation.viewer.ViewerSession

/**
 * All screens of the app. The grid screens and the viewer share a [androidx.compose.animation.SharedTransitionLayout], so a
 * tapped thumbnail grows into the viewer and the viewer's photo shrinks back into the grid.
 */
internal const val EnterFadeMillis = 180
internal const val ExitFadeMillis = 120
internal const val EnterSlideMillis = 300
internal const val ExitSlideMillis = 300

private val topLevelRoutes = setOf(
    GalleryDestination.Pictures.route,
    GalleryDestination.Albums.route,
)

private fun isTopLevelRoute(route: String?): Boolean {
    if (route == null) return false
    val base = route.substringBefore('/').substringBefore('?')
    return topLevelRoutes.contains(base) || base.endsWith("Pictures") || base.endsWith("Albums")
}

// Helper to find the index of a top-level route to determine slide direction
private fun topLevelIndex(route: String?): Int {
    if (route == null) return -1
    val base = route.substringBefore('/').substringBefore('?')
    return when {
        base == GalleryDestination.Pictures.route || base.endsWith("Pictures") -> 0
        base == GalleryDestination.Albums.route || base.endsWith("Albums") -> 1
        else -> -1
    }
}

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
            ScreenTag.base = destination.route.orEmpty().substringBefore('/').substringBefore('?').substringAfterLast('.')
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    NavHost(
        navController = navController,
        startDestination = GalleryRoutes.Pictures,
        modifier = modifier.fillMaxSize(),
        enterTransition = {
            val initialRoute = initialState.destination.route
            val targetRoute = targetState.destination.route

            if (isTopLevelRoute(initialRoute) && isTopLevelRoute(targetRoute)) {
                val initialIdx = topLevelIndex(initialRoute)
                val targetIdx = topLevelIndex(targetRoute)
                val slideDirection = if (targetIdx > initialIdx) 1 else -1

                slideInHorizontally(
                    animationSpec = tween(EnterSlideMillis),
                    initialOffsetX = { fullWidth -> slideDirection * fullWidth }
                ) + fadeIn(tween(EnterFadeMillis))
            } else {
                scaleIn(animationSpec = tween(EnterSlideMillis), initialScale = 0.8f) +
                        slideInHorizontally(
                            animationSpec = tween(EnterSlideMillis),
                            initialOffsetX = { fullWidth -> fullWidth },
                        ) + fadeIn(tween(EnterFadeMillis))
            }
        },
        exitTransition = {
            val initialRoute = initialState.destination.route
            val targetRoute = targetState.destination.route

            if (isTopLevelRoute(initialRoute) && isTopLevelRoute(targetRoute)) {
                val initialIdx = topLevelIndex(initialRoute)
                val targetIdx = topLevelIndex(targetRoute)
                val slideDirection = if (targetIdx > initialIdx) -1 else 1

                slideOutHorizontally(
                    animationSpec = tween(ExitSlideMillis),
                    targetOffsetX = { fullWidth -> slideDirection * (fullWidth / 3) }
                ) + fadeOut(tween(ExitFadeMillis))
            } else {
                scaleOut(animationSpec = tween(ExitSlideMillis), targetScale = 0.8f) +
                        slideOutHorizontally(
                            animationSpec = tween(ExitSlideMillis),
                            targetOffsetX = { fullWidth -> -fullWidth / 3 },
                        ) + fadeOut(tween(ExitFadeMillis))
            }
        },
        popEnterTransition = {
            val initialRoute = initialState.destination.route
            val targetRoute = targetState.destination.route

            if (isTopLevelRoute(initialRoute) && isTopLevelRoute(targetRoute)) {
                val initialIdx = topLevelIndex(initialRoute)
                val targetIdx = topLevelIndex(targetRoute)
                val slideDirection = if (targetIdx > initialIdx) 1 else -1

                slideInHorizontally(
                    animationSpec = tween(EnterSlideMillis),
                    initialOffsetX = { fullWidth -> slideDirection * fullWidth }
                ) + fadeIn(tween(EnterFadeMillis))
            } else {
                if (isTopLevelRoute(initialRoute)) {
                    fadeIn(tween(EnterFadeMillis))
                } else {
                    scaleIn(animationSpec = tween(EnterSlideMillis), initialScale = 0.8f) +
                            slideInHorizontally(
                                animationSpec = tween(EnterSlideMillis),
                                initialOffsetX = { fullWidth -> -fullWidth / 3 },
                            ) + fadeIn(tween(EnterFadeMillis))
                }
            }
        },
        popExitTransition = {
            val initialRoute = initialState.destination.route
            val targetRoute = targetState.destination.route

            if (isTopLevelRoute(initialRoute) && isTopLevelRoute(targetRoute)) {
                val initialIdx = topLevelIndex(initialRoute)
                val targetIdx = topLevelIndex(targetRoute)
                val slideDirection = if (targetIdx > initialIdx) -1 else 1

                slideOutHorizontally(
                    animationSpec = tween(ExitSlideMillis),
                    targetOffsetX = { fullWidth -> slideDirection * (fullWidth / 3) }
                ) + fadeOut(tween(ExitFadeMillis))
            } else {
                if (isTopLevelRoute(initialRoute)) {
                    fadeOut(tween(ExitFadeMillis))
                } else {
                    scaleOut(animationSpec = tween(ExitSlideMillis), targetScale = 0.8f) +
                            slideOutHorizontally(
                                animationSpec = tween(ExitSlideMillis),
                                targetOffsetX = { fullWidth -> fullWidth },
                            ) + fadeOut(tween(ExitFadeMillis))
                }
            }
        },
    ) {
        sharedComposable<GalleryRoutes.Pictures> { entry ->
            PicturesScreen(
                onMediaClick = { mediaIndex, mediaId ->
                    viewerSession.open(GalleryRoutes.viewer(mediaIndex, mediaId), origin = entry.id)
                },
                viewed = viewerSession.viewedFor(entry.id),
                onViewedConsumed = viewerSession::consumeViewed,
            )
        }
        composable<GalleryRoutes.Albums> { entry ->
            AlbumsScreen(
                onAlbumClick = { bucketId ->
                    if (bucketId == SpecialAlbums.PEOPLE) {
                        navController.navigate(GalleryRoutes.People)
                    } else {
                        navController.navigate(GalleryRoutes.Album(bucketId))
                    }
                },
                onMediaClick = { bucketId, mediaIndex, mediaId ->
                    viewerSession.open(GalleryRoutes.viewer(mediaIndex, mediaId, bucketId), origin = entry.id)
                },
                onPlaySlideshow = { bucketId ->
                    navController.navigate(GalleryRoutes.Slideshow(bucketId = bucketId))
                },
                viewed = viewerSession.viewedFor(entry.id),
                onViewedConsumed = viewerSession::consumeViewed,
            )
        }
        sharedComposable<GalleryRoutes.Album> { entry ->
            val route = entry.toRoute<GalleryRoutes.Album>()
            AlbumScreen(
                bucketId = route.bucketId,
                onBack = { navController.popBackStack() },
                onMediaClick = { bucketId, mediaIndex, mediaId ->
                    viewerSession.open(GalleryRoutes.viewer(mediaIndex, mediaId, bucketId), origin = entry.id)
                },
                onPlaySlideshow = { bucketId -> navController.navigate(GalleryRoutes.Slideshow(bucketId = bucketId)) },
                viewed = viewerSession.viewedFor(entry.id),
                onViewedConsumed = viewerSession::consumeViewed,
            )
        }
        sharedComposable<GalleryRoutes.Search> { entry ->
            SearchScreen(
                onBack = { navController.popBackStack() },
                onMediaClick = { mediaIndex, mediaId, query ->
                    viewerSession.open(GalleryRoutes.viewer(mediaIndex, mediaId, query = query), origin = entry.id)
                },
                viewed = viewerSession.viewedFor(entry.id),
                onViewedConsumed = viewerSession::consumeViewed,
            )
        }
        sharedComposable<GalleryRoutes.Images> { entry ->
            ImagesScreen(
                onBack = { navController.popBackStack() },
                onMediaClick = { mediaIndex, mediaId, query ->
                    viewerSession.open(GalleryRoutes.viewer(mediaIndex, mediaId, query = query), origin = entry.id)
                },
                viewed = viewerSession.viewedFor(entry.id),
                onViewedConsumed = viewerSession::consumeViewed,
            )
        }
        sharedComposable<GalleryRoutes.Videos> { entry ->
            VideosScreen(
                onBack = { navController.popBackStack() },
                onMediaClick = { mediaIndex, mediaId, query ->
                    viewerSession.open(GalleryRoutes.viewer(mediaIndex, mediaId, query = query), origin = entry.id)
                },
                viewed = viewerSession.viewedFor(entry.id),
                onViewedConsumed = viewerSession::consumeViewed,
            )
        }
        composable<GalleryRoutes.Stories> {
            StoriesScreen(
                onBack = { navController.popBackStack() },
                onStoryClick = { fromDay, toDay -> navController.navigate(GalleryRoutes.Story(fromDay, toDay)) },
            )
        }
        sharedComposable<GalleryRoutes.Story> { entry ->
            StoryScreen(
                onBack = { navController.popBackStack() },
                onMediaClick = { mediaIndex, mediaId, query ->
                    viewerSession.open(GalleryRoutes.viewer(mediaIndex, mediaId, query = query), origin = entry.id)
                },
                onPlaySlideshow = { query -> navController.navigate(GalleryRoutes.Slideshow(query = query)) },
                viewed = viewerSession.viewedFor(entry.id),
                onViewedConsumed = viewerSession::consumeViewed,
            )
        }
        sharedComposable<GalleryRoutes.Map> { entry ->
            MapScreen(
                onBack = { navController.popBackStack() },
                onOpenCluster = { cluster ->
                    val query = cluster.box.toQuery()
                    if (cluster.count == 1) {
                        // One photo: straight to the viewer, which then holds just that photo.
                        viewerSession.open(
                            route = GalleryRoutes.viewer(0, cluster.coverId, query = query),
                            origin = entry.id,
                            originRoute = GalleryRoutes.MAP,
                        )
                    } else {
                        navController.navigate(GalleryRoutes.Place(query))
                    }
                },
                viewed = viewerSession.viewedFor(entry.id),
                onViewedConsumed = viewerSession::consumeViewed,
            )
        }
        sharedComposable<GalleryRoutes.Place> { entry ->
            PlaceScreen(
                onBack = { navController.popBackStack() },
                onMediaClick = { mediaIndex, mediaId, query ->
                    viewerSession.open(GalleryRoutes.viewer(mediaIndex, mediaId, query = query), origin = entry.id)
                },
                onPlaySlideshow = { query -> navController.navigate(GalleryRoutes.Slideshow(query = query)) },
                viewed = viewerSession.viewedFor(entry.id),
                onViewedConsumed = viewerSession::consumeViewed,
            )
        }
        composable<GalleryRoutes.Duplicates> { entry ->
            DuplicatesScreen(
                onBack = { navController.popBackStack() },
                onCompare = { group, initialMediaId ->
                    viewerSession.open(
                        route = GalleryRoutes.viewer(
                            mediaIndex = 0,
                            mediaId = initialMediaId,
                            query = com.femi.gallery.domain.search.SearchQueryParser.idsQuery(group.items.map { it.id }),
                        ),
                        origin = entry.id,
                    )
                },
            )
        }
        composable<GalleryRoutes.Similar> { entry ->
            SimilarPhotosScreen(
                onBack = { navController.popBackStack() },
                onCompare = { group, initialMediaId ->
                    viewerSession.open(
                        route = GalleryRoutes.viewer(
                            mediaIndex = 0,
                            mediaId = initialMediaId,
                            query = com.femi.gallery.domain.search.SearchQueryParser.idsQuery(group.items.map { it.id }),
                        ),
                        origin = entry.id,
                    )
                },
            )
        }
        composable<GalleryRoutes.People> {
            PeopleScreen(
                onBack = { navController.popBackStack() },
                onPersonClick = { personId -> navController.navigate(GalleryRoutes.Person(personId)) },
            )
        }
        sharedComposable<GalleryRoutes.Person> { entry ->
            PersonScreen(
                onBack = { navController.popBackStack() },
                onMediaClick = { mediaIndex, mediaId, ids ->
                    viewerSession.open(
                        route = GalleryRoutes.viewer(
                            mediaIndex = mediaIndex,
                            mediaId = mediaId,
                            query = com.femi.gallery.domain.search.SearchQueryParser.idsQuery(ids),
                        ),
                        origin = entry.id,
                    )
                },
            )
        }
        composable<GalleryRoutes.Slideshow> {
            SlideshowScreen(
                onClose = { navController.popBackStack() },
            )
        }
        composable<GalleryRoutes.Editor> {
            EditorScreen(
                onClose = { navController.popBackStack() },
            )
        }
        composable<GalleryRoutes.Trim> {
            TrimScreen(
                onClose = { navController.popBackStack() },
            )
        }
        composable<GalleryRoutes.Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/** A typed destination that takes part in the grid-to-viewer shared transition. */
internal inline fun <reified T : Any> NavGraphBuilder.sharedComposable(
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        // `this` is the AnimatedContentScope of this destination, which the transition needs.
        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) { content(entry) }
    }
}

/** A legacy string destination that takes part in the grid-to-viewer shared transition. */
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
