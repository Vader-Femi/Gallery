package com.femi.gallery.presentation.common

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/**
 * False while the screen is still animating in (including the photo growing out of the grid), true
 * once it has stopped moving. Heavy work such as making a video player is kept until then, so it
 * never lands in the middle of an animation. Without a navigation transition it is true at once.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun rememberTransitionSettled(): Boolean {
    val sharedScope = LocalSharedTransitionScope.current
    val visibilityScope = LocalAnimatedVisibilityScope.current
    var settled by remember { mutableStateOf(sharedScope == null || visibilityScope == null) }
    if (!settled && sharedScope != null && visibilityScope != null) {
        LaunchedEffect(sharedScope, visibilityScope) {
            snapshotFlow {
                visibilityScope.transition.currentState == EnterExitState.Visible && !sharedScope.isTransitionActive
            }.first { it }
            settled = true
        }
    }
    return settled
}
