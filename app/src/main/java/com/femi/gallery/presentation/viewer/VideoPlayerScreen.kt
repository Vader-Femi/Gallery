package com.femi.gallery.presentation.viewer

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.femi.gallery.R
import com.femi.gallery.presentation.common.ImmersiveMode
import kotlinx.coroutines.delay
import androidx.media3.common.MediaItem as PlayerMediaItem

/**
 * A video on the whole screen with modern player controls (play and pause, seek bar, time, PiP,
 * orientation lock, touch controls lock, double-tap to play/pause, and optional background audio).
 */
@OptIn(UnstableApi::class)
@Composable
fun FullVideoPlayer(
    uri: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    startPositionMs: Long = 0L,
    playAudioInBackground: Boolean = false,
    /** Something to show at the right end of the top bar, such as a button. */
    trailing: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var resumePositionMs by rememberSaveable(uri) { mutableLongStateOf(startPositionMs) }
    var resumePlaying by rememberSaveable(uri) { mutableStateOf(true) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var failed by remember(uri) { mutableStateOf(false) }

    var isLocked by rememberSaveable { mutableStateOf(false) }
    var unlockButtonVisible by remember { mutableStateOf(false) }

    var isLandscape by remember { mutableStateOf(false) }
    var isInPip by remember { mutableStateOf(false) }

    val supportsPip = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    if (supportsPip && activity is androidx.activity.ComponentActivity) {
        DisposableEffect(activity) {
            val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
                isInPip = info.isInPictureInPictureMode
            }
            activity.addOnPictureInPictureModeChangedListener(listener)
            onDispose {
                activity.removeOnPictureInPictureModeChangedListener(listener)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val audioAttributes = remember {
        AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()
    }

    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
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
        if (!playAudioInBackground && !isInPip) {
            player.pause()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (resumePlaying && !player.isPlaying) {
            player.play()
        }
    }

    LaunchedEffect(unlockButtonVisible) {
        if (unlockButtonVisible) {
            delay(3000)
            unlockButtonVisible = false
        }
    }

    BackHandler {
        if (isLocked) {
            isLocked = false
        } else {
            onClose()
        }
    }

    ImmersiveMode(enabled = !controlsVisible || isLocked || isInPip)

    val enterPip = {
        if (supportsPip && activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val videoSize = player.videoSize
            val params = PictureInPictureParams.Builder().apply {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = videoSize.width.toFloat() / videoSize.height.toFloat()
                    if (ratio in 0.42f..2.38f) {
                        setAspectRatio(Rational(videoSize.width, videoSize.height))
                    }
                }
            }.build()
            activity.enterPictureInPictureMode(params)
        }
    }

    val toggleOrientation = {
        val newOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        isLandscape = !isLandscape
        activity?.requestedOrientation = newOrientation
    }

    val gestureDetector = remember(player, isLocked) {
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!isLocked) {
                    player.playWhenReady = !player.playWhenReady
                    return true
                }
                return false
            }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isLocked) {
                    unlockButtonVisible = !unlockButtonVisible
                    return true
                }
                return false
            }
        })
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                // The controls sit above the navigation bar instead of under it.
                .windowInsetsPadding(if (isInPip) WindowInsets(0, 0, 0, 0) else WindowInsets.navigationBars),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = !isLocked && !isInPip
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    controllerShowTimeoutMs = ControlsTimeoutMs
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == View.VISIBLE && !isLocked
                        },
                    )
                    setOnTouchListener { _, event ->
                        if (isLocked) {
                            gestureDetector.onTouchEvent(event)
                            true
                        } else {
                            gestureDetector.onTouchEvent(event)
                        }
                    }
                }
            },
            update = {
                it.player = player
                it.useController = !isLocked && !isInPip
                it.setOnTouchListener { _, event ->
                    if (isLocked) {
                        gestureDetector.onTouchEvent(event)
                        true
                    } else {
                        gestureDetector.onTouchEvent(event)
                    }
                }
            },
            onRelease = { it.player = null },
        )

        // Top bar with controls: Back, Title/Trailing, Screen Rotate, PiP, and Lock
        AnimatedVisibility(
            visible = controlsVisible && !isLocked && !isInPip,
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

                // Rotate orientation button
                IconButton(onClick = toggleOrientation) {
                    Icon(
                        imageVector = Icons.Outlined.ScreenRotation,
                        contentDescription = stringResource(R.string.player_rotate),
                        tint = Color.White,
                    )
                }

                // Picture-in-Picture button (API 26+)
                if (supportsPip) {
                    IconButton(onClick = enterPip) {
                        Icon(
                            imageVector = Icons.Outlined.PictureInPictureAlt,
                            contentDescription = stringResource(R.string.player_pip),
                            tint = Color.White,
                        )
                    }
                }

                // Lock controls button
                IconButton(onClick = {
                    isLocked = true
                    controlsVisible = false
                }) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = stringResource(R.string.player_lock),
                        tint = Color.White,
                    )
                }
            }
        }

        // Floating Unlock button when controls are locked
        AnimatedVisibility(
            visible = isLocked && unlockButtonVisible && !isInPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 36.dp),
        ) {
            Surface(
                onClick = {
                    isLocked = false
                    unlockButtonVisible = false
                    controlsVisible = true
                },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.8f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LockOpen,
                        contentDescription = stringResource(R.string.player_unlock),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.player_unlock),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
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

/** Helper to extract Activity from Context. */
private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** How long the controls stay on screen after the last touch while the video plays. */
private const val ControlsTimeoutMs = 3_000
