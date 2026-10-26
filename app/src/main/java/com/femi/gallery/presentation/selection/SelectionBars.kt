package com.femi.gallery.presentation.selection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
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

/** Top "N selected" bar and bottom action bar shown while selecting photos with Expressive M3 styling and ease-in/ease-out animations. */
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
    AnimatedVisibility(
        visible = active,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(250, easing = FastOutSlowInEasing)),
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Row (
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ){
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {

                if (count != 0)
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
                        text = stringResource(
                            R.string.selection_count,
                            count
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.width(12.dp))
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
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

    AnimatedVisibility(
        visible = active,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(250, easing = FastOutSlowInEasing)),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEach { action ->
                    BarAction(action.icon, action.label, enabled = count > 0, onClick = action.onClick)
                }
                if (moreActions.isNotEmpty()) MoreMenu(moreActions, enabled = count > 0)
            }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 3.dp),
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun MoreMenu(actions: List<SelectionAction>, enabled: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 3.dp),
    ) {
        IconButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.action_more),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = stringResource(R.string.action_more),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}
