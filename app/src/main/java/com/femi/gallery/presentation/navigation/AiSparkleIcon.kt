package com.femi.gallery.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.femi.gallery.R

@Composable
fun AiBadgeIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant // Google Gemini Blue
) {
    Icon(
        imageVector = Icons.Filled.AutoAwesome,
        contentDescription = stringResource(R.string.search_ai),
        tint = tint,
        modifier = modifier
    )
}