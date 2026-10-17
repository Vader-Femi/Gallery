package com.femi.gallery.presentation.locked

import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.femi.gallery.R
import com.femi.gallery.core.security.LockSession
import com.femi.gallery.presentation.common.findActivity

/**
 * Shows [content] only after the user authenticates. While it is on screen the window is marked
 * secure, which hides it from screenshots, screen recording and the recent-apps preview.
 */
@Composable
fun LockedGate(
    session: LockSession,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    val unlocked by session.unlocked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    val available = BiometricAuth.isAvailable(context)
    val title = stringResource(R.string.locked_prompt_title)
    val subtitle = stringResource(R.string.locked_prompt_subtitle)

    SecureWindow()

    if (unlocked) {
        content()
    } else {
        val prompt = {
            if (activity != null && available) {
                BiometricAuth.authenticate(activity, title, subtitle, onSuccess = session::unlock)
            }
        }
        LaunchedEffect(available) { prompt() }
        LockedPlaceholder(available = available, onUnlock = prompt, onClose = onClose)
    }
}

@Composable
private fun LockedPlaceholder(available: Boolean, onUnlock: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(if (available) R.string.locked_title else R.string.locked_no_screen_lock_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(if (available) R.string.locked_body else R.string.locked_no_screen_lock_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (available) {
            Button(onClick = onUnlock) { Text(stringResource(R.string.locked_unlock)) }
        }
        TextButton(onClick = onClose) { Text(stringResource(R.string.viewer_back)) }
    }
}

/**
 * How many [SecureWindow]s are currently on screen. Screens overlap while navigating (the album
 * is still composed while the viewer opens on top of it), so the flag may only be cleared when
 * the last one leaves; otherwise the album disposing would switch off the viewer's protection.
 */
private var secureWindowCount = 0

/** Blocks screenshots, screen recording and the recent-apps thumbnail while this is composed. */
@Composable
fun SecureWindow() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        secureWindowCount++
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            secureWindowCount = maxOf(0, secureWindowCount - 1)
            if (secureWindowCount == 0) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
