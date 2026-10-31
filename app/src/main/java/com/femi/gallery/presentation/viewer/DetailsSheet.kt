package com.femi.gallery.presentation.viewer

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.femi.gallery.R
import com.femi.gallery.domain.model.MediaDetails
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(
    details: MediaDetails,
    onDismiss: () -> Unit,
    onSetWallpaper: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val item = details.item
    val formatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    }
    val taken = remember(item.dateTakenMillis, formatter) {
        formatter.format(Instant.ofEpochMilli(item.dateTakenMillis).atZone(ZoneId.systemDefault()))
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.details_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))

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
            if (!item.isVideo && onSetWallpaper != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        onSetWallpaper()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_set_as_wallpaper))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
