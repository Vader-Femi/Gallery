package com.femi.gallery.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.femi.gallery.presentation.navigation.GalleryDestination
import com.femi.gallery.presentation.navigation.GalleryNavHost
import com.femi.gallery.presentation.permission.MediaPermissionViewModel
import com.femi.gallery.presentation.permission.PermissionScreen

@Composable
fun GalleryApp(
    permissionViewModel: MediaPermissionViewModel = hiltViewModel(),
) {
    val state by permissionViewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        permissionViewModel.refresh()
        onPauseOrDispose { }
    }

    if (state.hasAccess) {
        GalleryScaffold()
    } else {
        PermissionScreen(
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                GalleryDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(GalleryDestination.Pictures.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
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
