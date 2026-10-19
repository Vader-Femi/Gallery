package com.femi.gallery.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.femi.gallery.domain.model.SpecialAlbums
import com.femi.gallery.presentation.common.ChromeState
import com.femi.gallery.presentation.common.LocalChromeState
import com.femi.gallery.presentation.navigation.GalleryDestination
import com.femi.gallery.presentation.navigation.GalleryNavHost
import com.femi.gallery.presentation.navigation.GalleryRoutes
import com.femi.gallery.presentation.permission.MediaPermissionViewModel
import com.femi.gallery.presentation.launch.LaunchRequest
import com.femi.gallery.presentation.permission.PermissionScreen
import com.femi.gallery.presentation.picker.PickerScreen
import com.femi.gallery.presentation.viewer.ExternalViewerScreen

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

    LifecycleResumeEffect(Unit) {
        permissionViewModel.refresh()
        onPauseOrDispose { }
    }

    when {
        // A single photo opened by another app needs no library access, only the URI it was given.
        request is LaunchRequest.ViewExternal -> ExternalViewerScreen(
            uri = request.uri,
            isVideo = request.isVideo,
            onClose = onFinish,
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

@Composable
private fun GalleryScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val chrome = LocalChromeState.current
    val lockSession = hiltViewModel<AppViewModel>().lockSession

    // The Locked folder stays open only while the app is in the foreground and the user is
    // looking at it (its album or a photo viewed from it).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { lockSession.lock() }
    val route = currentDestination?.route
    val arguments = backStackEntry?.arguments
    val inLockedFolder = (route == GalleryRoutes.ALBUM || route == GalleryRoutes.VIEWER) &&
        arguments?.containsKey("bucketId") == true &&
        arguments.getLong("bucketId") == SpecialAlbums.LOCKED
    LaunchedEffect(inLockedFolder) { if (!inLockedFolder) lockSession.lock() }

    val isAlbumDetail = currentDestination?.route == GalleryRoutes.ALBUM
    val isStoryDetail = currentDestination?.route == GalleryRoutes.STORY
    val isTopLevel = isAlbumDetail || isStoryDetail || GalleryDestination.entries.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // The full-screen viewer takes over the whole window.
            if (isTopLevel && !chrome.bottomBarHidden) NavigationBar {
                GalleryDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true ||
                            (isAlbumDetail && destination == GalleryDestination.Albums) ||
                            (isStoryDetail && destination == GalleryDestination.Stories),
                        onClick = {
                            if (isAlbumDetail && destination == GalleryDestination.Albums) {
                                // Tapping Albums while inside an album goes back to the album list.
                                navController.popBackStack(GalleryDestination.Albums.route, inclusive = false)
                            } else if (isStoryDetail && destination == GalleryDestination.Stories) {
                                navController.popBackStack(GalleryDestination.Stories.route, inclusive = false)
                            } else {
                                navController.navigate(destination.route) {
                                    popUpTo(GalleryDestination.Pictures.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        GalleryNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
