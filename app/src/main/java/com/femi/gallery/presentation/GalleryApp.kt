package com.femi.gallery.presentation

import android.util.Log
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.femi.gallery.domain.model.SpecialAlbums
import com.femi.gallery.presentation.common.ChromeState
import com.femi.gallery.presentation.common.LocalChromeState
import com.femi.gallery.presentation.common.LocalSharedTransitionScope
import com.femi.gallery.presentation.common.LocalViewerShowing
import com.femi.gallery.presentation.navigation.FloatingNavigationBar
import com.femi.gallery.presentation.navigation.GalleryDestination
import com.femi.gallery.presentation.navigation.GalleryNavHost
import com.femi.gallery.presentation.navigation.GalleryRoutes
import com.femi.gallery.presentation.navigation.MenuBottomSheet
import com.femi.gallery.presentation.permission.MediaPermissionViewModel
import com.femi.gallery.presentation.launch.LaunchRequest
import com.femi.gallery.presentation.permission.PermissionScreen
import com.femi.gallery.presentation.picker.PickerScreen
import com.femi.gallery.presentation.viewer.ExternalViewerScreen
import com.femi.gallery.presentation.viewer.ViewerHost
import com.femi.gallery.presentation.viewer.ViewerSession

@Composable
fun GalleryApp(
    launch: LaunchRequest = LaunchRequest.Normal,
    onPick: (uris: List<String>) -> Unit = {},
    onFinish: () -> Unit = {},
    permissionViewModel: MediaPermissionViewModel = hiltViewModel(),
) {
    val state by permissionViewModel.uiState.collectAsStateWithLifecycle()

    // "Open Gallery" in the external viewer switches to the normal app and stays there.
    var openedGallery by rememberSaveable { mutableStateOf(false) }
    val request = if (openedGallery) LaunchRequest.Normal else launch

    LaunchedEffect(launch) {
        if (launch is LaunchRequest.ViewExternal) {
            openedGallery = false
        }
    }

    LifecycleResumeEffect(Unit) {
        permissionViewModel.refresh()
        onPauseOrDispose { }
    }

    when {
        // A single photo opened by another app needs no library access, only the URI it was given.
        request is LaunchRequest.ViewExternal -> ExternalViewerScreen(
            uri = request.uri,
            isVideo = request.isVideo,
            onClose = {
                openedGallery = true
            },
            onOpenGallery = { openedGallery = true },
        )

        state.hasAccess -> CompositionLocalProvider(LocalChromeState provides remember { ChromeState() }) {
            if (request is LaunchRequest.Pick) {
                PickerScreen(
                    filter = request.filter,
                    allowMultiple = request.allowMultiple,
                    onPick = onPick,
                    onCancel = onFinish,
                )
            } else {
                GalleryScaffold()
            }
        }

        else -> PermissionScreen(
            permanentlyDenied = state.permanentlyDenied,
            onPermissionResult = permissionViewModel::onPermissionResult,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun GalleryScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val chrome = LocalChromeState.current

    // The photo viewer has a navigation of its own and floats above the whole screen (see ViewerSession).
    val viewerNavController = rememberNavController()
    val viewerEntry by viewerNavController.currentBackStackEntryAsState()
    val viewerShowing = viewerEntry?.destination?.route == GalleryRoutes.VIEWER
    val viewerSession = remember(viewerNavController) { ViewerSession(viewerNavController) }

    val appViewModel = hiltViewModel<AppViewModel>()
    val lockSession = appViewModel.lockSession

    // New photos get their place read whenever the app comes to the front.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { appViewModel.scanLocations() }

    // The Locked folder stays open only while the app is in the foreground and the user is
    // looking at it (its album or a photo viewed from it).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { lockSession.lock() }
    val route = currentDestination?.route
    val arguments = backStackEntry?.arguments
    val inLockedFolder = ((currentDestination?.hasRoute<GalleryRoutes.Album>() == true || route == GalleryRoutes.ALBUM || route == GalleryRoutes.VIEWER) &&
        arguments?.containsKey("bucketId") == true &&
        arguments.getLong("bucketId") == SpecialAlbums.LOCKED)
    LaunchedEffect(inLockedFolder) { if (!inLockedFolder) lockSession.lock() }

    val isAlbumDetail = currentDestination?.hasRoute<GalleryRoutes.Album>() == true || currentDestination?.route == GalleryRoutes.ALBUM
    val isTopLevel = currentDestination?.hasRoute<GalleryRoutes.Pictures>() == true ||
        currentDestination?.hasRoute<GalleryRoutes.Albums>() == true ||
        GalleryDestination.entries.any { destination ->
            currentDestination?.hierarchy?.any { it.route == destination.route } == true
        }

    var menuSheetOpen by rememberSaveable { mutableStateOf(false) }

    // One shared-transition layout around the screens and the viewer, so a photo can travel between them.
    SharedTransitionLayout {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalViewerShowing provides viewerShowing,
        ) {
            // Reports (tag SharedPhoto) when a photo is travelling between a grid and the viewer.
            val transitionScope = this
            LaunchedEffect(transitionScope) {
                snapshotFlow { transitionScope.isTransitionActive }
                    .collect { Log.d("SharedPhoto", "photo transition running = $it") }
            }
            val windowInfo = LocalWindowInfo.current
            val density = LocalDensity.current
            val isExpanded = with(density) { windowInfo.containerSize.width.toDp() } >= 600.dp

            val isFullScreen = currentDestination?.hasRoute<GalleryRoutes.Slideshow>() == true ||
                currentDestination?.hasRoute<GalleryRoutes.Editor>() == true ||
                currentDestination?.hasRoute<GalleryRoutes.Trim>() == true ||
                route?.startsWith("slideshow") == true || route?.startsWith("editor") == true || route?.startsWith("trim") == true
            val isSearchRoute = currentDestination?.hasRoute<GalleryRoutes.Search>() == true || route?.startsWith("search") == true
            val showNavRail = isExpanded && !chrome.bottomBarHidden && !isFullScreen && !viewerShowing
            val showBottomBar = !isExpanded && isTopLevel && !chrome.bottomBarHidden && !isFullScreen && !viewerShowing
            val showNavBar = showNavRail || showBottomBar

            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                ) { innerPadding ->
                    val topPadding = if (isFullScreen) 0.dp else innerPadding.calculateTopPadding()
                    val startPadding = if (showNavRail) 104.dp else 0.dp
                    GalleryNavHost(
                        navController = navController,
                        viewerSession = viewerSession,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topPadding, start = startPadding),
                    )
                }

                if (showNavBar) {
                    FloatingNavigationBar(
                        modifier = Modifier.align(if (isExpanded) Alignment.CenterStart else Alignment.BottomCenter),
                        isVertical = isExpanded,
                        destinations = GalleryDestination.entries,
                        currentDestinationSelected = { destination ->
                            (destination == GalleryDestination.Pictures && currentDestination?.hasRoute<GalleryRoutes.Pictures>() == true) ||
                                (destination == GalleryDestination.Albums && (currentDestination?.hasRoute<GalleryRoutes.Albums>() == true || isAlbumDetail)) ||
                                currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        },
                        onDestinationClick = { destination ->
                            menuSheetOpen = false
                            if (isAlbumDetail && destination == GalleryDestination.Albums) {
                                // Tapping Albums while inside an album goes back to the album list.
                                val popped = navController.popBackStack<GalleryRoutes.Albums>(inclusive = false)
                                if (!popped) {
                                    navController.popBackStack(GalleryDestination.Albums.route, inclusive = false)
                                }
                            } else {
                                val targetRoute: Any = when (destination) {
                                    GalleryDestination.Pictures -> GalleryRoutes.Pictures
                                    GalleryDestination.Albums -> GalleryRoutes.Albums
                                }
                                navController.navigate(targetRoute) {
                                    popUpTo<GalleryRoutes.Pictures> { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        onSearchClick = {
                            menuSheetOpen = false
                            if (!isSearchRoute) {
                                navController.navigate(GalleryRoutes.Search())
                            }
                        },
                        onMenuClick = {
                            menuSheetOpen = !menuSheetOpen
                        },
                        isSearchSelected = isSearchRoute,
                        isMenuSelected = menuSheetOpen,
                    )
                }

                if (menuSheetOpen) {
                    MenuBottomSheet(
                        onDismiss = { menuSheetOpen = false },
                        onItemClick = { itemRoute ->
                            menuSheetOpen = false
                            when (itemRoute) {
                                    GalleryDestination.Pictures.route -> {
                                        navController.navigate(GalleryRoutes.Pictures) {
                                            popUpTo<GalleryRoutes.Pictures> { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    GalleryDestination.Albums.route -> {
                                        navController.navigate(GalleryRoutes.Albums) {
                                            popUpTo<GalleryRoutes.Pictures> { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    GalleryRoutes.IMAGES -> navController.navigate(GalleryRoutes.Images)
                                    GalleryRoutes.VIDEOS -> navController.navigate(GalleryRoutes.Videos)
                                    GalleryRoutes.STORIES -> navController.navigate(GalleryRoutes.Stories)
                                    GalleryRoutes.MAP -> navController.navigate(GalleryRoutes.Map)
                                    GalleryRoutes.PEOPLE -> navController.navigate(GalleryRoutes.People)
                                    GalleryRoutes.DUPLICATES -> navController.navigate(GalleryRoutes.Duplicates)
                                    GalleryRoutes.SIMILAR -> navController.navigate(GalleryRoutes.Similar)
                                    GalleryRoutes.SETTINGS -> navController.navigate(GalleryRoutes.Settings)
                                    is String -> {
                                        if (itemRoute.startsWith("album/")) {
                                            val bucketId = itemRoute.removePrefix("album/").toLongOrNull()
                                            if (bucketId != null) {
                                                navController.navigate(GalleryRoutes.Album(bucketId))
                                            } else {
                                                navController.navigate(itemRoute)
                                            }
                                        } else {
                                            navController.navigate(itemRoute)
                                        }
                                    }
                                    else -> navController.navigate(itemRoute)
                                }
                        },
                    )
                }

                ViewerHost(
                    session = viewerSession,
                    onEdit = { uri ->
                        viewerSession.close()
                        navController.navigate(GalleryRoutes.Editor(uri))
                    },
                    onTrim = { uri ->
                        viewerSession.close()
                        navController.navigate(GalleryRoutes.Trim(uri))
                    },
                )
            }
        }
    }
}
