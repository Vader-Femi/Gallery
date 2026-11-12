package com.femi.gallery.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard Material 3 Window Width Size Classes:
 * - [Compact]: Phone portrait, width < 600 dp
 * - [Medium]: Tablets in portrait, foldables unfolded, width 600 dp to 839 dp
 * - [Expanded]: Large tablets in landscape, desktop, Dex, width >= 840 dp
 */
enum class WindowWidthClass {
    Compact,
    Medium,
    Expanded;

    val isCompact: Boolean get() = this == Compact
    val isMedium: Boolean get() = this == Medium
    val isExpanded: Boolean get() = this == Expanded
    val isMediumOrExpanded: Boolean get() = this != Compact
}

object WindowWidthBreakpoints {
    val Medium: Dp = 600.dp
    val Expanded: Dp = 840.dp

    const val MEDIUM_DP: Int = 600
    const val EXPANDED_DP: Int = 840

    fun fromWidthDp(widthDp: Int): WindowWidthClass = when {
        widthDp >= EXPANDED_DP -> WindowWidthClass.Expanded
        widthDp >= MEDIUM_DP -> WindowWidthClass.Medium
        else -> WindowWidthClass.Compact
    }

    fun fromWidth(width: Dp): WindowWidthClass = when {
        width >= Expanded -> WindowWidthClass.Expanded
        width >= Medium -> WindowWidthClass.Medium
        else -> WindowWidthClass.Compact
    }
}

/**
 * Reactively resolves the current [WindowWidthClass] using modern window container size.
 */
@Composable
fun rememberWindowWidthClass(): WindowWidthClass {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val widthDp = with(density) { windowInfo.containerSize.width.toDp() }
    return WindowWidthBreakpoints.fromWidth(widthDp)
}
