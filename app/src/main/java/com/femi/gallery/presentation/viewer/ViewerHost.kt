package com.femi.gallery.presentation.viewer

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.femi.gallery.core.perf.ScreenTag
import com.femi.gallery.presentation.common.ViewedMedia
import com.femi.gallery.presentation.navigation.EnterFadeMillis
import com.femi.gallery.presentation.navigation.ExitFadeMillis
import com.femi.gallery.presentation.navigation.GalleryRoutes
import com.femi.gallery.presentation.navigation.sharedComposable

private const val EmptyRoute = "viewer-closed"

/**
 * Opens the photo viewer over the screen that is showing, and remembers which photo the user is on
 * so that screen can follow along.
 *
 * The viewer is not a screen of the app's navigation. A screen is replaced when another opens, but
 * the viewer has to float above the grid so the grid stays visible behind a photo that is being
 * dragged away (like Google Photos). So it has a navigation of its own, with one screen, and every
 * visit still gets its own view model with its arguments from the route.
 */
@Stable
class ViewerSession(internal val navController: NavHostController) {

    /** The photo the viewer is on, for the grid under it to scroll to. */
    var viewed by mutableStateOf<ViewedMedia?>(null)
        private set

    /** The screen that opened the viewer; only that grid follows along. */
    private var origin: String? = null

    /** Opens the viewer at [route] (see [GalleryRoutes.viewer]) over the screen identified by [origin]. */
    fun open(route: String, origin: String) {
        if (navController.currentDestination?.route == GalleryRoutes.VIEWER) return // a double tap
        this.origin = origin
        viewed = null
        navController.navigate(route)
    }

    fun close() {
        navController.popBackStack()
    }

    fun onViewed(mediaIndex: Int, mediaId: Long) {
        viewed = ViewedMedia(mediaIndex, mediaId)
    }

    /** The grid has scrolled to the viewed photo. */
    fun consumeViewed() {
        viewed = null
    }

    /** The viewed photo, for the screen [origin] (and for no other). */
    fun viewedFor(origin: String): ViewedMedia? = viewed.takeIf { this.origin == origin }
}

/**
 * The viewer's navigation: nothing while closed (so the screen below stays fully usable), the viewer
 * while open. Place it above everything, inside the shared-transition layout of the screen.
 */
@Composable
fun ViewerHost(
    session: ViewerSession,
    onEdit: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = session.navController

    // Tells the frame monitor that the viewer is what is on screen, not the grid under it.
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            ScreenTag.overlay = if (destination.route == GalleryRoutes.VIEWER) "viewer" else null
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
            ScreenTag.overlay = null
        }
    }

    NavHost(
        navController = navController,
        startDestination = EmptyRoute,
        modifier = modifier.fillMaxSize(),
        enterTransition = { fadeIn(tween(EnterFadeMillis)) },
        exitTransition = { fadeOut(tween(ExitFadeMillis)) },
        popEnterTransition = { fadeIn(tween(EnterFadeMillis)) },
        popExitTransition = { fadeOut(tween(ExitFadeMillis)) },
    ) {
        composable(EmptyRoute) { }
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
                onBack = session::close,
                onEdit = onEdit,
                // Tells the grid under the viewer which photo we are on.
                onViewedChange = session::onViewed,
            )
        }
    }
}
