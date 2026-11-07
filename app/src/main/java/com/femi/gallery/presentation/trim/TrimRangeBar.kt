package com.femi.gallery.presentation.trim

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.tooling.preview.Preview
import com.femi.gallery.ui.theme.GalleryTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.femi.gallery.R
import com.femi.gallery.domain.video.TrimMath
import com.femi.gallery.domain.video.TrimRange
import kotlin.math.roundToInt

private val HandleWidth = 16.dp
private val TouchSize = 48.dp
private val BarHeight = 48.dp
private const val StepMs = 1_000L

/**
 * The bar under the video: the whole length of the video, the kept part highlighted, two handles to drag (each with a
 * 48 dp touch area and accessibility actions that move it by one second) and a thin line for the playing position.
 */
@Composable
fun TrimRangeBar(
    durationMs: Long,
    range: TrimRange,
    positionMs: Long,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentRange by rememberUpdatedState(range)
    val currentDuration by rememberUpdatedState(durationMs)
    val onStart by rememberUpdatedState(onStartChange)
    val onEnd by rememberUpdatedState(onEndChange)

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(TouchSize)) {
        val density = LocalDensity.current
        // The handles sit inside the bar: a handle at 0 starts at the left edge, one at 100 % ends at the right edge.
        val travelPx = with(density) { (maxWidth - HandleWidth).toPx() }.coerceAtLeast(1f)
        fun xFor(ms: Long): Int = (TrimMath.msToFraction(ms, durationMs) * travelPx).roundToInt()
        fun msFor(deltaPx: Float): Long = (deltaPx / travelPx * currentDuration).toLong()

        val startX = xFor(range.startMs)
        val endX = xFor(range.endMs)
        val handleHalf = with(density) { HandleWidth.toPx() / 2 }

        // The track, the kept part and the playing position.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(BarHeight - 16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((startX + handleHalf).roundToInt(), 0) }
                .width(with(density) { (endX - startX).coerceAtLeast(0).toDp() })
                .height(BarHeight - 16.dp)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((xFor(positionMs) + handleHalf).roundToInt() - 1, 0) }
                .width(2.dp)
                .height(BarHeight)
                .background(MaterialTheme.colorScheme.onSurface),
        )

        val startLabel = stringResource(R.string.trim_start_handle)
        val endLabel = stringResource(R.string.trim_end_handle)
        val earlier = stringResource(R.string.trim_move_earlier)
        val later = stringResource(R.string.trim_move_later)

        Handle(
            x = startX,
            description = startLabel,
            state = TrimMath.format(range.startMs, tenths = true),
            moveEarlier = earlier,
            moveLater = later,
            onDrag = { delta -> onStart(currentRange.startMs + msFor(delta)) },
            onStep = { direction -> onStart(currentRange.startMs + direction * StepMs) },
        )
        Handle(
            x = endX,
            description = endLabel,
            state = TrimMath.format(range.endMs, tenths = true),
            moveEarlier = earlier,
            moveLater = later,
            onDrag = { delta -> onEnd(currentRange.endMs + msFor(delta)) },
            onStep = { direction -> onEnd(currentRange.endMs + direction * StepMs) },
        )
    }
}

@Composable
private fun Handle(
    x: Int,
    description: String,
    state: String,
    moveEarlier: String,
    moveLater: String,
    onDrag: (deltaPx: Float) -> Unit,
    onStep: (direction: Int) -> Unit,
) {
    val drag by rememberUpdatedState(onDrag)
    Box(
        modifier = Modifier
            // A 48 dp wide touch area centred on the 16 dp handle.
            .offset { IntOffset(x - ((TouchSize - HandleWidth).toPx() / 2).roundToInt(), 0) }
            .size(TouchSize)
            .semantics {
                contentDescription = description
                stateDescription = state
                customActions = listOf(
                    CustomAccessibilityAction(moveEarlier) { onStep(-1); true },
                    CustomAccessibilityAction(moveLater) { onStep(1); true },
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    drag(dragAmount)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(HandleWidth)
                .height(BarHeight)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
        )
    }
}

@Preview(name = "Trim Range Bar", showBackground = true)
@Composable
private fun TrimRangeBarPreview() {
    GalleryTheme(dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            TrimRangeBar(
                durationMs = 60_000L,
                range = TrimRange(startMs = 15_000L, endMs = 45_000L),
                positionMs = 25_000L,
                onStartChange = {},
                onEndChange = {},
            )
        }
    }
}
