package com.femi.gallery.presentation.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.femi.gallery.R
import com.femi.gallery.domain.model.MediaDetails
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Modal bottom sheet used for phones and compact portrait screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(
    details: MediaDetails,
    onDismiss: () -> Unit,
    scrollToBottom: Boolean = false,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        DetailsContent(
            details = details,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            scrollToBottom = scrollToBottom,
        )
    }
}

/** Docked side panel used for tablets, foldables, and wide landscape screens. */
@Composable
fun DetailsPanel(
    details: MediaDetails,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    scrollToBottom: Boolean = false,
) {
    Surface(
        modifier = modifier
            .width(360.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.details_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.editor_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            DetailsContent(
                details = details,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                scrollToBottom = scrollToBottom,
            )
        }
    }
}

@Composable
fun DetailsContent(
    details: MediaDetails,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    scrollToBottom: Boolean = false,
) {
    val context = LocalContext.current
    val item = details.item
    val formatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    }
    val taken = remember(item.dateTakenMillis, formatter) {
        formatter.format(Instant.ofEpochMilli(item.dateTakenMillis).atZone(ZoneId.systemDefault()))
    }

    LaunchedEffect(scrollToBottom, details.detectedText) {
        if (scrollToBottom && !details.detectedText.isNullOrBlank()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        DetailRow(stringResource(R.string.details_file), item.displayName)
        DetailRow(stringResource(R.string.details_taken), taken)
        if (item.relativePath.isNotEmpty()) {
            DetailRow(stringResource(R.string.details_folder), item.relativePath)
        }
        if (item.width > 0 && item.height > 0) {
            DetailRow(stringResource(R.string.details_resolution), formatResolution(item.width, item.height))
        }
        DetailRow(stringResource(R.string.details_size), Formatter.formatShortFileSize(context, item.sizeBytes))
        if (item.isVideo && item.durationMillis > 0) {
            DetailRow(stringResource(R.string.details_duration), formatDuration(item.durationMillis))
        }
        if (item.isVideo && details.fps != null && details.fps > 0.0) {
            DetailRow(stringResource(R.string.details_framerate), formatFps(details.fps))
        }
        details.camera?.let { DetailRow(stringResource(R.string.details_camera), it) }

        val settings = listOfNotNull(
            details.aperture?.let { String.format(Locale.US, "f/%.1f", it) },
            details.exposureSeconds?.let(::formatExposure),
            details.iso?.let { "ISO $it" },
            details.focalLengthMm?.let { String.format(Locale.US, "%.1f mm", it) },
        )
        if (settings.isNotEmpty()) {
            DetailRow(stringResource(R.string.details_settings), settings.joinToString("  ·  "))
        }
        if (details.latitude != null && details.longitude != null) {
            DetailRow(
                stringResource(R.string.details_location),
                String.format(Locale.US, "%.5f, %.5f", details.latitude, details.longitude),
            )
        }

        if (!details.detectedText.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(if (item.isVideo) R.string.details_detected_text_video else R.string.details_detected_text),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    SelectionContainer {
                        Text(
                            text = details.detectedText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Media text", details.detectedText))
                            Toast.makeText(context, R.string.text_copied, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_copy_text))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
