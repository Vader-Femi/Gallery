package com.femi.gallery.presentation.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.femi.gallery.R
import com.femi.gallery.domain.edit.MarkupMath
import com.femi.gallery.domain.edit.MarkupPalette
import com.femi.gallery.domain.edit.MarkupSticker
import com.femi.gallery.domain.edit.MarkupText

/** The text being typed: [id] is the text that is being changed, or null for a new one. */
private class TextEntry(val id: Long?, val initial: String)

/** What the markup tool offers: drawing with a pen or highlighter, text, and emoji stickers. */
@Composable
fun MarkupPanel(state: EditorUiState, viewModel: EditorViewModel) {
    var entry by remember { mutableStateOf<TextEntry?>(null) }
    val selected = state.edit.markup.firstOrNull { it.id == state.selectedMarkupId }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(R.string.markup_draw, state.markupMode == MarkupMode.Draw) { viewModel.setMarkupMode(MarkupMode.Draw) }
                ModeChip(R.string.markup_text, state.markupMode == MarkupMode.Text) { viewModel.setMarkupMode(MarkupMode.Text) }
                ModeChip(R.string.markup_stickers, state.markupMode == MarkupMode.Sticker) { viewModel.setMarkupMode(MarkupMode.Sticker) }
            }
            IconButton(onClick = viewModel::undoMarkup, enabled = state.canUndoMarkup) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = stringResource(R.string.markup_undo))
            }
            IconButton(onClick = viewModel::deleteSelected, enabled = selected != null) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.markup_delete))
            }
        }

        // Colours: for the pen, and for the selected text.
        if (state.markupMode != MarkupMode.Sticker) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MarkupPalette.colors.forEach { color ->
                    Swatch(color = color, chosen = color == state.penColor) { viewModel.setPenColor(color) }
                }
            }
        }

        when (state.markupMode) {
            MarkupMode.Draw -> {
                MarkupSlider(stringResource(R.string.markup_thickness), state.penWidth, 0.004f..0.04f, viewModel::setPenWidth)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = state.highlighter,
                        onClick = { viewModel.setHighlighter(!state.highlighter) },
                        label = { Text(stringResource(R.string.markup_highlighter)) },
                    )
                }
            }

            MarkupMode.Text -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { entry = TextEntry(null, "") }) { Text(stringResource(R.string.markup_add_text)) }
                    if (selected is MarkupText) {
                        TextButton(onClick = { entry = TextEntry(selected.id, selected.text) }) {
                            Text(stringResource(R.string.markup_edit_text))
                        }
                    }
                }
                if (selected is MarkupText) {
                    MarkupSlider(stringResource(R.string.markup_size), selected.sizeFraction, 0.03f..0.3f, viewModel::setSelectedSize)
                }
            }

            MarkupMode.Sticker -> {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    MarkupPalette.stickers.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(role = Role.Button) { viewModel.addSticker(emoji) }
                                .semantics { contentDescription = emoji },
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 28.sp) }
                    }
                }
                if (selected is MarkupSticker) {
                    MarkupSlider(stringResource(R.string.markup_size), selected.sizeFraction, 0.05f..0.6f, viewModel::setSelectedSize)
                }
            }
        }
    }

    entry?.let { current ->
        var text by remember(current) { mutableStateOf(current.initial) }
        AlertDialog(
            onDismissRequest = { entry = null },
            title = { Text(stringResource(R.string.markup_text)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.markup_text_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank() || current.id != null,
                    onClick = {
                        if (current.id == null) viewModel.addText(text) else viewModel.editText(current.id, text)
                        entry = null
                    },
                ) { Text(stringResource(R.string.markup_done)) }
            },
            dismissButton = { TextButton(onClick = { entry = null }) { Text(stringResource(R.string.picker_cancel)) } },
        )
    }
}

@Composable
private fun ModeChip(label: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(label)) })
}

/** A colour to pick: a 48 dp touch area around a 32 dp disc, with a ring when it is the one in use. */
@Composable
private fun Swatch(color: Int, chosen: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.markup_color)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = description
                selected = chosen
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(color), CircleShape)
                .border(BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else Color.Gray), CircleShape),
        )
    }
}

@Composable
private fun MarkupSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}

/** Ask before cropping, turning or straightening removes the markup. */
@Composable
fun ConfirmClearMarkupDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.markup_clear_title)) },
        text = { Text(stringResource(R.string.markup_clear_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.markup_clear_confirm)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.editor_keep_editing)) } },
    )
}
