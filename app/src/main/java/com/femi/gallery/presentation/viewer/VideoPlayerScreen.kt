package com.femi.gallery.presentation.viewer

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.femi.gallery.R
import com.femi.gallery.presentation.common.ImmersiveMode
import androidx.media3.common.MediaItem as PlayerMediaItem

/**
 * A video on the whole screen with the usual player controls (play and pause, seek bar, time),
 * playing with sound from the start or from [startPositionMs]. It is used by "Open with" from other
 * apps and by the viewer's Play button, so it relies on nothing but the video's address: it needs no
 * thumbnail, no known size and no place in the library.
 *
 * Tap the video to show or hide the controls and the top bar. Leaving the app pauses the video, and
 * it carries on from the same spot (and the same play or pause state) when the screen is rebuilt,
 * for example after turning the phone.
 */
@OptIn(UnstableApi::class)
@Composable
fun FullVideoPlayer(
    uri: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    startPositionMs: Long = 0L,
    /** Something to show at the right end of the top bar, such as a button. */
    trailing: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    var resumePositionMs by rememberSaveable(uri) { mutableLongStateOf(startPositionMs) }
    var resumePlaying by rememberSaveable(uri) { mutableStateOf(true) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var failed by remember(uri) { mutableStateOf(false) }

    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            setMediaItem(PlayerMediaItem.fromUri(uri))
            seekTo(resumePositionMs)
            playWhenReady = resumePlaying
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Away from the screen the video stops; coming back (or a rebuilt screen) carries on where it was.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        resumePositionMs = player.currentPosition.coerceAtLeast(0L)
        resumePlaying = player.playWhenReady
        player.pause()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (resumePlaying) player.play()
    }

    BackHandler(onBack = onClose)
    ImmersiveMode(enabled = !controlsVisible)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // Nothing underneath (the viewer, when this is shown above it) may receive touches.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                // The controls sit above the navigation bar instead of under it.
                .windowInsetsPadding(WindowInsets.navigationBars),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    controllerShowTimeoutMs = ControlsTimeoutMs
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility -> controlsVisible = visibility == View.VISIBLE },
                    )
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
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
                trailing()
            }
        }

        if (failed) {
            Text(
                text = stringResource(R.string.player_error),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            )
        }
    }
}

/** How long the controls stay on screen after the last touch while the video plays. */
private const val ControlsTimeoutMs = 3_000
