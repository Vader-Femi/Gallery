package com.femi.gallery.presentation.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.femi.gallery.R
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaType
import com.femi.gallery.presentation.common.ImmersiveMode

/**
 * Shows one photo or video that another app opened with "Open with" / "Review". The file may
 * not be in the library at all (a chat attachment, a download), so there is no swiping, only
 * zoom and playback, plus a button to open the whole gallery.
 */
@Composable
fun ExternalViewerScreen(
    uri: String,
    isVideo: Boolean,
    onClose: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    ImmersiveMode(enabled = !chromeVisible)

    // Only the URI is known, so the rest of the item is filled with neutral values.
    val item = remember(uri, isVideo) {
        MediaItem(
            id = 0L,
            uri = uri,
            displayName = "",
            mimeType = if (isVideo) "video/*" else "image/*",
            type = if (isVideo) MediaType.Video else MediaType.Image,
            dateTakenMillis = 0L,
            dateAddedMillis = 0L,
            width = 0,
            height = 0,
            sizeBytes = 0L,
            durationMillis = 0L,
            bucketId = 0L,
            bucketName = "",
            relativePath = "",
            isFavorite = false,
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isVideo) {
            VideoPage(item = item, isCurrent = true, onChromeChange = { chromeVisible = it })
        } else {
            PhotoPage(item = item, isCurrent = true, onTap = { chromeVisible = !chromeVisible })
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.viewer_back),
                        tint = Color.White,
                    )
                }
                Box(modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenGallery) {
                    Text(stringResource(R.string.external_open_gallery), color = Color.White)
                }
            }
        }
    }
}
