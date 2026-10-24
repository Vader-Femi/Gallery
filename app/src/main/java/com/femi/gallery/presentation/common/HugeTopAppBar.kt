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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.femi.gallery.R

/**
 * A prominent, large top app bar used across screens to give titles room and improve
 * one-handed reachability, matching One UI / Material 3 large header design.
 * Text size shrinks smoothly as the bar collapses when scrolled to the top.
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
    val collapsedFraction = scrollBehavior?.state?.collapsedFraction ?: 0f
    var fontSize = lerp(start = 32.sp, stop = 28.sp, fraction = collapsedFraction)
    if (onBack != null) fontSize = lerp(start = 32.sp, stop = 22.sp, fraction = collapsedFraction)
    val fontWeight = if (collapsedFraction > 0.5f) FontWeight.SemiBold else FontWeight.Bold

    LargeTopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                ),
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
