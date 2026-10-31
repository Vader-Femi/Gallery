package com.femi.gallery.presentation.selection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.femi.gallery.R

/** One button in the bottom action bar. */
class SelectionAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/** Top selection header bar showing "Cancel", count selected, and "Select all". */
@Composable
fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .wrapContentSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.selection_cancel)
                    )
                }

                Text(
                    text = if (count == 0) "" else stringResource(R.string.selection_count, count),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.width(12.dp))
            }
        }

        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            TextButton(onClick = onSelectAll) {
                Text(
                    stringResource(R.string.selection_select_all),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Bottom action bar shown while selecting photos.
 * Uses [HorizontalFloatingToolbar] for correct M3 Expressive shape, shadow and animated
 * show/hide, and delegates enter/exit animation specs to [MaterialTheme.motionScheme].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.SelectionBottomBar(
    active: Boolean = true,
    count: Int,
    actions: List<SelectionAction>,
    moreActions: List<SelectionAction> = emptyList(),
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = active,
        modifier = modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        actions.forEach { action ->
            IconButton(onClick = action.onClick, enabled = count > 0) {
                Icon(
                    action.icon,
                    contentDescription = action.label,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (moreActions.isNotEmpty()) {
            MoreMenu(moreActions, enabled = count > 0)
        }
    }
}

/** Top "N selected" bar and bottom action bar shown while selecting photos. */
@Composable
fun BoxScope.SelectionBars(
    active: Boolean = true,
    count: Int,
    actions: List<SelectionAction>,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    /** Less common actions, shown in a "More" menu at the end of the bar. */
    moreActions: List<SelectionAction> = emptyList(),
) {
    // Use motionScheme specs so the animation inherits the expressive motion token.
    AnimatedVisibility(
        visible = active,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        ) + fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        ) + fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        SelectionTopBar(
            count = count,
            onClose = onClose,
            onSelectAll = onSelectAll,
        )
    }

    SelectionBottomBar(
        active = active,
        count = count,
        actions = actions,
        moreActions = moreActions,
    )
}

@Composable
private fun MoreMenu(actions: List<SelectionAction>, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(
            Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.action_more),
            modifier = Modifier.size(22.dp),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                leadingIcon = { Icon(action.icon, contentDescription = null) },
                onClick = {
                    expanded = false
                    action.onClick()
                },
            )
        }
    }
}
