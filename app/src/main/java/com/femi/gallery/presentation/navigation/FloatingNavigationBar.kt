package com.femi.gallery.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A floating, capsule-shaped navigation bar mirroring Google Photos' modern bottom bar.
 * Places the text label beside the icon horizontally in a pill layout.
 *
 * @param destinations List of top-level navigation destinations.
 * @param currentDestinationSelected Lambda to determine if a destination is currently active.
 * @param onDestinationClick Callback invoked when a navigation item is selected.
 * @param alwaysShowLabel If true, all items display icon and text side-by-side.
 *                        If false, only the selected item expands to show text beside the icon.
 */
@Composable
fun FloatingNavigationBar(
    destinations: List<GalleryDestination>,
    currentDestinationSelected: (GalleryDestination) -> Boolean,
    onDestinationClick: (GalleryDestination) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    alwaysShowLabel: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.wrapContentWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { destination ->
                    val isSelected = currentDestinationSelected(destination)
                    FloatingNavigationItem(
                        destination = destination,
                        isSelected = isSelected,
                        alwaysShowLabel = alwaysShowLabel,
                        onClick = { onDestinationClick(destination) },
                    )
                }
            }
        }

        // Space between the 3 main options and the search button
        Spacer(modifier = Modifier.width(12.dp))

        // Search floating button with AI logo on top
        Box(contentAlignment = Alignment.TopEnd) {
            Surface(
                onClick = onSearchClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.size(56.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // AI Logo positioned on top of the search button
            AiBadgeIcon(
                modifier = Modifier
                    .offset(x = (-12).dp, y = (13).dp)
                    .size(14.dp)
            )
        }
    }
}

@Composable
private fun FloatingNavigationItem(
    destination: GalleryDestination,
    isSelected: Boolean,
    alwaysShowLabel: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "backgroundColor",
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "contentColor",
    )

    Surface(
        selected = isSelected,
        onClick = onClick,
        shape = CircleShape,
        color = backgroundColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (isSelected) {
                Icon(
                    imageVector = destination.selectedIcon,
                    contentDescription = stringResource(destination.label),
                )
            }
            AnimatedVisibility(
                visible = isSelected || alwaysShowLabel,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = stringResource(destination.label),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
