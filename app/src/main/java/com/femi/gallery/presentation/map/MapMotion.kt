package com.femi.gallery.presentation.map

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import com.femi.gallery.domain.geo.MapViewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The map's moving parts that last longer than a touch: coasting after a throw, smooth zoom and the
 * fly between two views. One movement runs at a time; starting another (or touching the map, see
 * [stop]) ends the one before. [scope] must come from the composition, because animation needs its frame clock.
 *
 * Every step changes the viewport through [update], which works from the latest viewport, so steps
 * and the user's own gestures never overwrite each other with stale values.
 */
class MapMotion(
    private val scope: CoroutineScope,
    private val update: ((MapViewport) -> MapViewport) -> Unit,
    private val current: () -> MapViewport?,
) {
    private var job: Job? = null

    /** Ends whatever movement is running (the map keeps where it had got to). */
    fun stop() {
        job?.cancel()
        job = null
    }

    /** Carries on moving at [velocity] (pixels per second) and slows down by itself. */
    fun fling(velocity: Velocity, width: Double, height: Double) {
        stop()
        job = scope.launch {
            var last = Offset.Zero
            val converter = Offset.VectorConverter
            AnimationState(converter, Offset.Zero, converter.convertToVector(Offset(velocity.x, velocity.y)))
                .animateDecay(exponentialDecay(frictionMultiplier = FLING_FRICTION)) {
                    val step = value - last
                    last = value
                    update { it.panned(step.x.toDouble(), step.y.toDouble(), width, height) }
                }
        }
    }

    /** Zooms smoothly to [targetZoom], keeping the point under ([anchorX], [anchorY]) where it is. */
    fun zoomAbout(anchorX: Double, anchorY: Double, targetZoom: Double, width: Double, height: Double) {
        stop()
        val start = current()?.zoom ?: return
        job = scope.launch {
            animate(0f, 1f, animationSpec = tween(ZOOM_MS, easing = FastOutSlowInEasing)) { fraction, _ ->
                val zoom = start + (targetZoom - start) * fraction
                update { it.zoomedAbout(anchorX, anchorY, zoom, width, height) }
            }
        }
    }

    /** Flies the map from where it is to [target]. */
    fun flyTo(target: MapViewport, width: Double, height: Double) {
        stop()
        val start = current() ?: return
        job = scope.launch {
            animate(0f, 1f, animationSpec = tween(FLY_MS, easing = FastOutSlowInEasing)) { fraction, _ ->
                update { MapViewport.between(start, target, fraction.toDouble(), width, height) }
            }
        }
    }

    private companion object {
        const val ZOOM_MS = 260
        const val FLY_MS = 550

        /** Higher stops sooner; 1 is the standard scroll feel, a map wants to glide a little less far. */
        const val FLING_FRICTION = 1.4f
    }
}
