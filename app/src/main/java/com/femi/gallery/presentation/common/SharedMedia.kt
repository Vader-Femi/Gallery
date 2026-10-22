package com.femi.gallery.presentation.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
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

/**
 * Whether the photo viewer is on screen over the grid; null where the viewer is not an overlay (the
 * picker, previews). The viewer sits above the grid instead of replacing it, so the grid has to
 * report "my photo is gone" itself when the viewer opens: see [gridSharedBounds].
 */
val LocalViewerShowing = compositionLocalOf<Boolean?> { null }

/**
 * Like [mediaSharedBounds], for a photo in a grid that stays on screen under the viewer. The grid is
 * never navigated away from, so its own screen scope would say "visible" for ever and nothing would
 * move. This gives the photo a scope of its own that turns "hidden" while the viewer is showing,
 * which is what makes the photo travel to and from the viewer.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.gridSharedBounds(mediaId: Long): Modifier {
    val viewerShowing = LocalViewerShowing.current ?: return mediaSharedBounds(mediaId)
    val transitionScope = LocalSharedTransitionScope.current
    if (transitionScope == null || mediaId <= 0L) return this
    // Always starts out visible, even when this photo is first composed while the viewer is already
    // opening: starting hidden would leave nothing to animate from.
    val state = remember { MutableTransitionState(EnterExitState.Visible) }
    state.targetState = if (viewerShowing) EnterExitState.PostExit else EnterExitState.Visible
    val transition = rememberTransition(state, label = "grid photo visibility")
    val visibilityScope = remember(transition) { PhotoVisibilityScope(transition) }
    return with(transitionScope) {
        this@gridSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "media-$mediaId"),
            animatedVisibilityScope = visibilityScope,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

/** A visibility scope that is just a transition: shared bounds only need its state, not an animation. */
private class PhotoVisibilityScope(override val transition: Transition<EnterExitState>) : AnimatedVisibilityScope {
    override fun Modifier.animateEnterExit(
        enter: EnterTransition,
        exit: ExitTransition,
        label: String,
    ): Modifier = this
}
