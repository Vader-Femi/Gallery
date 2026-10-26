package com.femi.gallery.presentation.editor

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.PhotoFilter
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.femi.gallery.R
import com.femi.gallery.domain.edit.ColorMatrices
import com.femi.gallery.domain.edit.EditFilter
import com.femi.gallery.domain.edit.EditState
import kotlin.math.roundToInt

private val PanelColor = Color(0xFF1C1C1E)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorScreen(
    onClose: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val requestClose: () -> Unit = { if (state.edit.isUnchanged) onClose() else confirmDiscard = true }
    BackHandler(onBack = requestClose)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.Saved -> {
                    Toast.makeText(context, R.string.toast_edit_saved, Toast.LENGTH_SHORT).show()
                    onClose()
                }
                EditorEvent.SaveFailed ->
                    Toast.makeText(context, R.string.toast_edit_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Only changes to the colors need a new filter; crop drags happen far more often.
    val edit = state.edit
    val previewFilter = remember(edit.brightness, edit.contrast, edit.saturation, edit.warmth, edit.filter) {
        ColorFilter.colorMatrix(ColorMatrix(edit.colorMatrix()))
    }

    Surface(color = Color(0xFF111111), contentColor = Color.White) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            EditorTopBar(
                canSave = !state.loading && !state.saving && !edit.isUnchanged,
                canReset = !edit.isUnchanged && !state.saving,
                onClose = requestClose,
                onReset = viewModel::reset,
                onSave = viewModel::save,
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Preview(state = state, viewModel = viewModel, colorFilter = previewFilter)
                if (state.saving) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) { LoadingIndicator() }
                }
            }

            Surface(color = PanelColor, contentColor = Color.White) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (state.tool) {
                        EditorTool.Crop -> CropPanel(state, viewModel)
                        EditorTool.Adjust -> AdjustPanel(edit, viewModel)
                        EditorTool.Filters -> FiltersPanel(state, viewModel)
                    }
                    ToolTabs(selected = state.tool, onSelect = viewModel::selectTool)
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_body)) },
            confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.editor_discard_confirm)) } },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.editor_keep_editing)) }
            },
        )
    }
}

@Composable
private fun EditorTopBar(
    canSave: Boolean,
    canReset: Boolean,
    onClose: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.editor_close))
        }
        Box(modifier = Modifier.weight(1f))
        TextButton(onClick = onReset, enabled = canReset) { Text(stringResource(R.string.editor_reset)) }
        Button(onClick = onSave, enabled = canSave, modifier = Modifier.padding(end = 8.dp)) {
            Text(stringResource(R.string.editor_save))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Preview(state: EditorUiState, viewModel: EditorViewModel, colorFilter: ColorFilter) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            state.loading -> LoadingIndicator()
            state.loadFailed -> Text(stringResource(R.string.editor_load_failed))
            else -> {
                val cropping = state.tool == EditorTool.Crop
                val bitmap = (if (cropping) state.oriented else state.cropped) ?: return@Box
                val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                // The image and the crop overlay share these bounds, which keeps their math aligned.
                val bounds = Modifier.fillMaxSize().padding(16.dp)
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = colorFilter,
                    modifier = bounds,
                )
                if (cropping) {
                    CropOverlay(
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        crop = state.edit.crop,
                        onDragCorner = viewModel::dragCropCorner,
                        onMove = viewModel::moveCrop,
                        modifier = bounds,
                    )
                }
            }
        }
    }
}

@Composable
private fun CropPanel(state: EditorUiState, viewModel: EditorViewModel) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(CropAspect.entries) { aspect ->
                FilterChip(
                    selected = state.aspect == aspect,
                    onClick = { viewModel.selectAspect(aspect) },
                    label = { Text(stringResource(aspect.label)) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = { viewModel.rotate(clockwise = false) }) {
                Icon(Icons.Outlined.RotateLeft, contentDescription = stringResource(R.string.editor_rotate_left))
            }
            IconButton(onClick = { viewModel.rotate(clockwise = true) }) {
                Icon(Icons.Outlined.RotateRight, contentDescription = stringResource(R.string.editor_rotate_right))
            }
            IconButton(onClick = viewModel::flipHorizontal) {
                Icon(Icons.Outlined.Flip, contentDescription = stringResource(R.string.editor_flip))
            }
        }
    }
}

@Composable
private fun AdjustPanel(edit: EditState, viewModel: EditorViewModel) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        AdjustSlider(R.string.adjust_brightness, edit.brightness, viewModel::setBrightness)
        AdjustSlider(R.string.adjust_contrast, edit.contrast, viewModel::setContrast)
        AdjustSlider(R.string.adjust_saturation, edit.saturation, viewModel::setSaturation)
        AdjustSlider(R.string.adjust_warmth, edit.warmth, viewModel::setWarmth)
    }
}

@Composable
private fun AdjustSlider(@StringRes label: Int, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(96.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = -1f..1f,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = (value * 100).roundToInt().toString(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(36.dp),
        )
    }
}

@Composable
private fun FiltersPanel(state: EditorUiState, viewModel: EditorViewModel) {
    val bitmap = state.cropped ?: state.oriented
    val thumbnail: ImageBitmap? = remember(bitmap) { bitmap?.asImageBitmap() }
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(EditFilter.entries) { filter ->
            val selected = state.edit.filter == filter
            Column(
                modifier = Modifier.clickable { viewModel.setFilter(filter) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray)
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        ),
                ) {
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = ColorFilter.colorMatrix(ColorMatrix(ColorMatrices.filter(filter))),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    text = stringResource(filterLabel(filter)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@StringRes
private fun filterLabel(filter: EditFilter): Int = when (filter) {
    EditFilter.None -> R.string.filter_none
    EditFilter.Vivid -> R.string.filter_vivid
    EditFilter.Warm -> R.string.filter_warm
    EditFilter.Cool -> R.string.filter_cool
    EditFilter.Mono -> R.string.filter_mono
    EditFilter.Noir -> R.string.filter_noir
    EditFilter.Sepia -> R.string.filter_sepia
    EditFilter.Fade -> R.string.filter_fade
    EditFilter.Dramatic -> R.string.filter_dramatic
}

@Composable
private fun ToolTabs(selected: EditorTool, onSelect: (EditorTool) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ToolTab(Icons.Outlined.Crop, R.string.editor_tool_crop, selected == EditorTool.Crop) { onSelect(EditorTool.Crop) }
        ToolTab(Icons.Outlined.Tune, R.string.editor_tool_adjust, selected == EditorTool.Adjust) { onSelect(EditorTool.Adjust) }
        ToolTab(Icons.Outlined.PhotoFilter, R.string.editor_tool_filters, selected == EditorTool.Filters) { onSelect(EditorTool.Filters) }
    }
}

@Composable
private fun ToolTab(icon: ImageVector, @StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else Color.White
    Column(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Text(text = stringResource(label), style = MaterialTheme.typography.labelMedium, color = color)
    }
}
