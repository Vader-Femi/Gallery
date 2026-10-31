package com.femi.gallery.presentation.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.femi.gallery.R

/**
 * A prominent, large top app bar used across screens to give titles room and improve
 * one-handed reachability, matching Material 3 Expressive large header design.
 *
 * Fix 10: Removed manual font-size lerp. [LargeTopAppBar] already collapses from
 * headlineMedium → titleLarge via its built-in scroll behaviour; the manual lerp was
 * fighting that logic and producing a non-standard result.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HugeTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surface,
    ),
) {
    LargeTopAppBar(
        title = {
            Text(
                text = title,
                // Let LargeTopAppBar drive style via Typography tokens (headlineMedium
                // when expanded, titleLarge when collapsed) — no manual interpolation needed.
                style = MaterialTheme.typography.headlineMedium,
                maxLines = if (onBack != null) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(start = 12.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.viewer_back),
                        )
                    }
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = colors,
        modifier = modifier,
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}
