package com.femi.gallery.presentation.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * How long a photo takes to travel between its grid cell and the viewer. The screen that is leaving
 * is removed when its own exit transition ends, and that would cut the flight short, so the viewer's
 * exit must last at least this long (see [PhotoCloseMillis]).
 */
const val PhotoTravelMillis = 300

/** The viewer's exit transition: a little longer than the photo's flight, so it is never cut off. */
const val PhotoCloseMillis = PhotoTravelMillis + 40

private val PhotoBoundsTransform = BoundsTransform { _, _ ->
    tween<Rect>(durationMillis = PhotoTravelMillis, easing = FastOutSlowInEasing)
}

/**
 * An overlay clip that morphs between a circular bubble (56 dp) and a full rectangular photo
 * during shared bounds transitions between the Map and the Viewer.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val BubbleMorphOverlayClip: SharedTransitionScope.OverlayClip = object : SharedTransitionScope.OverlayClip {
    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        val path = Path()
        val bubblePx = with(density) { 56.dp.toPx() }
        val minDim = minOf(bounds.width, bounds.height)
        val maxRadius = minDim / 2f

        // Progress of expansion: 0f at bubble size (or smaller), smoothly reaching 1f as width expands.
        val progress = ((bounds.width - bubblePx) / (bubblePx * 2.5f)).coerceIn(0f, 1f)
        val radius = maxRadius * (1f - progress)

        val roundRect = RoundRect(
            rect = Rect(Offset.Zero, bounds.size),
            cornerRadius = CornerRadius(radius, radius),
        )
        path.addRoundRect(roundRect)
        path.translate(bounds.topLeft)
        return path
    }
}

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
fun Modifier.mediaSharedBounds(
    mediaId: Long,
    clipInOverlay: SharedTransitionScope.OverlayClip? = null,
): Modifier {
    val transitionScope = LocalSharedTransitionScope.current
    val visibilityScope = LocalAnimatedVisibilityScope.current
    if (transitionScope == null || visibilityScope == null || mediaId <= 0L) {
        LaunchedEffect(mediaId) {
            Log.d(
                SharedLogTag,
                "viewer photo $mediaId is NOT shared (transition scope ${transitionScope != null}, " +
                    "visibility scope ${visibilityScope != null}, valid id ${mediaId > 0L})",
            )
        }
        return this
    }
    LaunchedEffect(mediaId) { Log.d(SharedLogTag, "viewer photo $mediaId is shared") }
    return with(transitionScope) {
        if (clipInOverlay != null) {
            this@mediaSharedBounds.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "media-$mediaId"),
                animatedVisibilityScope = visibilityScope,
                boundsTransform = PhotoBoundsTransform,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                clipInOverlayDuringTransition = clipInOverlay,
            )
        } else {
            this@mediaSharedBounds.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "media-$mediaId"),
                animatedVisibilityScope = visibilityScope,
                boundsTransform = PhotoBoundsTransform,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            )
        }
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
fun Modifier.gridSharedBounds(
    mediaId: Long,
    clipInOverlay: SharedTransitionScope.OverlayClip? = null,
): Modifier {
    val viewerShowing = LocalViewerShowing.current ?: return mediaSharedBounds(mediaId, clipInOverlay)
    val transitionScope = LocalSharedTransitionScope.current
    if (transitionScope == null || mediaId <= 0L) {
        LaunchedEffect(mediaId) {
            Log.d(SharedLogTag, "grid photo $mediaId is NOT shared (transition scope ${transitionScope != null}, valid id ${mediaId > 0L})")
        }
        return this
    }
    // Start in PostExit if the viewer is already open so new cells don't play a spurious
    // transition during scrolling, but start in Visible when closed so opening can animate.
    val state = remember {
        MutableTransitionState(if (viewerShowing) EnterExitState.PostExit else EnterExitState.Visible)
    }
    state.targetState = if (viewerShowing) EnterExitState.PostExit else EnterExitState.Visible
    LaunchedEffect(mediaId, viewerShowing) { Log.d(SharedLogTag, "grid photo $mediaId: viewer showing = $viewerShowing") }
    val transition = rememberTransition(state, label = "grid photo visibility")
    val visibilityScope = remember(transition) { PhotoVisibilityScope(transition) }
    return with(transitionScope) {
        if (clipInOverlay != null) {
            this@gridSharedBounds.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "media-$mediaId"),
                animatedVisibilityScope = visibilityScope,
                boundsTransform = PhotoBoundsTransform,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                clipInOverlayDuringTransition = clipInOverlay,
            )
        } else {
            this@gridSharedBounds.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "media-$mediaId"),
                animatedVisibilityScope = visibilityScope,
                boundsTransform = PhotoBoundsTransform,
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            )
        }
    }
}

/**
 * Keeps this UI element (such as the viewer's top bar or action bar) rendered above shared elements
 * during an active shared-bounds transition, so transitioning photos don't render over it before
 * dropping under it once the transition ends.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.renderAboveSharedElements(zIndex: Float = 1f): Modifier {
    val transitionScope = LocalSharedTransitionScope.current ?: return this
    return with(transitionScope) {
        this@renderAboveSharedElements.renderInSharedTransitionScopeOverlay(
            zIndexInOverlay = zIndex,
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

private const val SharedLogTag = "SharedPhoto"
