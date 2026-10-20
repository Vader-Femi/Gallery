package com.femi.gallery.presentation.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** The scope that lets a thumbnail in a grid and the photo in the viewer animate as one element. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The screen currently being shown or hidden by navigation, needed to start a shared transition. */
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks this element as the photo with id [mediaId], so it moves smoothly between the grid and
 * the viewer. Without a transition scope (the picker, previews) or a real id it does nothing.
 *
 * Only the element that is about to be opened should carry it: registering hundreds of grid cells
 * would slow scrolling down for no benefit.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.mediaSharedBounds(mediaId: Long): Modifier {
    val transitionScope = LocalSharedTransitionScope.current
    val visibilityScope = LocalAnimatedVisibilityScope.current
    if (transitionScope == null || visibilityScope == null || mediaId <= 0L) return this
    return with(transitionScope) {
        this@mediaSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "media-$mediaId"),
            animatedVisibilityScope = visibilityScope,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}
