package com.femi.gallery.presentation.viewer

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon as AndroidIcon
import android.media.AudioManager
import android.util.Rational
import android.provider.OpenableColumns
import android.view.GestureDetector
import android.view.ViewConfiguration
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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
import androidx.media3.ui.PlayerNotificationManager
import androidx.media3.ui.PlayerView
import com.femi.gallery.MainActivity
import com.femi.gallery.R
import com.femi.gallery.presentation.common.ImmersiveMode
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import androidx.media3.common.MediaItem as PlayerMediaItem

private const val PIP_ACTION_PLAY_PAUSE = "com.femi.gallery.action.PIP_PLAY_PAUSE"
private const val PLAYBACK_CHANNEL_ID = "gallery_video_playback"
private const val PLAYBACK_NOTIFICATION_ID = 2001
private const val ControlsTimeoutMs = 3_000

private data class SwipeSeekInfo(
    val targetMs: Long,
    val deltaMs: Long,
    val durationMs: Long,
)

private data class SwipeVolumeInfo(
    val volume: Int,
    val maxVolume: Int,
)

private enum class DragMode { None, Seek, Volume }

/**
 * A video on the whole screen with modern player controls:
 * - Play/pause, seek bar, time display
 * - Single-tap toggle controls, double-tap play/pause
 * - Horizontal swipe to seek with real-time HUD
 * - Vertical swipe up/down to adjust volume with real-time HUD
 * - PiP mode with interactive play/pause controls (continuous playback without pause on enter)
 * - Orientation rotation lock
 * - Controls lock (touch-lock mode)
 * - Background audio with full system media notification and return-to-player intent
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
    val view = LocalView.current
    val systemTouchSlop = remember(context) { ViewConfiguration.get(context).scaledTouchSlop.toFloat() }
    val activity = remember(context) { context.findActivity() }
    val appName = stringResource(R.string.app_name)
    val windowInfo = LocalWindowInfo.current
    val screenWidthPx = remember(windowInfo) { windowInfo.containerSize.width.toFloat().coerceAtLeast(1f) }
    val screenHeightPx = remember(windowInfo) { windowInfo.containerSize.height.toFloat().coerceAtLeast(1f) }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var resumePositionMs by rememberSaveable(uri) { mutableLongStateOf(startPositionMs) }
    var resumePlaying by rememberSaveable(uri) { mutableStateOf(value = true) }
    var controlsVisible by rememberSaveable { mutableStateOf(value = true) }
    var failed by remember(uri) { mutableStateOf(value = false) }

    var isLocked by rememberSaveable { mutableStateOf(value = false) }
    var unlockButtonVisible by remember { mutableStateOf(value = false) }

    var isLandscape by remember { mutableStateOf(value = false) }
    var isInPip by remember { mutableStateOf(value = false) }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var swipeSeekInfo by remember { mutableStateOf<SwipeSeekInfo?>(null) }
    var swipeVolumeInfo by remember { mutableStateOf<SwipeVolumeInfo?>(null) }

    var dragMode by remember { mutableStateOf(DragMode.None) }
    var dragStartX by remember { mutableFloatStateOf(0f) }
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var gestureStartSeekMs by remember { mutableLongStateOf(0L) }
    var gestureStartVolume by remember { mutableIntStateOf(0) }

    val supportsPip = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    if (supportsPip && (activity is androidx.activity.ComponentActivity)) {
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

    // Interactive PiP action button (Play / Pause)
    val updatePipParams: (Boolean) -> Unit = remember(context, player, activity, supportsPip, playAudioInBackground) {
        { isPlaying ->
            if (supportsPip && (activity != null)) {
                val intent = Intent(PIP_ACTION_PLAY_PAUSE).setPackage(context.packageName)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    101,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                val title = context.getString(if (isPlaying) R.string.player_pause else R.string.player_play)
                val action = RemoteAction(
                    AndroidIcon.createWithResource(context, iconRes),
                    title,
                    title,
                    pendingIntent,
                )
                val videoSize = player.videoSize
                val params = PictureInPictureParams.Builder().apply {
                    if ((videoSize.width > 0) && (videoSize.height > 0)) {
                        val ratio = videoSize.width.toFloat() / videoSize.height.toFloat()
                        if (ratio in 0.42f..2.38f) {
                            setAspectRatio(Rational(videoSize.width, videoSize.height))
                        }
                    }
                    setActions(listOf(action))
                    // Home while a video plays keeps it playing in a small window; a person who chose background
                    // audio gets the audio only, as that setting says.
                    setAutoEnterEnabled(isPlaying && !playAudioInBackground)
                }.build()
                activity.setPictureInPictureParams(params)
            }
        }
    }

    // Broadcast receiver for PiP play/pause action button
    DisposableEffect(context, player) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == PIP_ACTION_PLAY_PAUSE) {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                }
            }
        }
        val filter = IntentFilter(PIP_ACTION_PLAY_PAUSE)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Media notification manager for background playback controls
    val notificationManager = remember(context, player, uri, appName) {
        val channel = NotificationChannel(
            PLAYBACK_CHANNEL_ID,
            appName,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.player_channel_description)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.createNotificationChannel(channel)

        PlayerNotificationManager.Builder(
            context,
            PLAYBACK_NOTIFICATION_ID,
            PLAYBACK_CHANNEL_ID,
        ).apply {
            setChannelNameResourceId(R.string.app_name)
            setSmallIconResourceId(R.mipmap.ic_launcher)
            setMediaDescriptionAdapter(
                object : PlayerNotificationManager.MediaDescriptionAdapter {
                    override fun getCurrentContentTitle(p: Player): CharSequence {
                        // The name of the video as the library knows it (the last part of its address is only a number).
                        val name = runCatching {
                            context.contentResolver.query(uri.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                                if (it.moveToFirst()) it.getString(0) else null
                            }
                        }.getOrNull()
                        return name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.player_video_title)
                    }

                    override fun createCurrentContentIntent(p: Player): PendingIntent? {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            action = Intent.ACTION_VIEW
                            data = uri.toUri()
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        return PendingIntent.getActivity(
                            context,
                            0,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    }

                    override fun getCurrentContentText(p: Player): CharSequence? = null

                    override fun getCurrentLargeIcon(
                        p: Player,
                        callback: PlayerNotificationManager.BitmapCallback,
                    ): Bitmap? = null
                },
            )
            setNotificationListener(
                object : PlayerNotificationManager.NotificationListener {
                    override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                        if (dismissedByUser) {
                            player.pause()
                        }
                    }
                },
            )
        }.build().apply {
            setUseFastForwardAction(true)
            setUseRewindAction(true)
            setUseNextAction(false)
            setUsePreviousAction(false)
            setUseStopAction(true)
        }
    }

    DisposableEffect(player, playAudioInBackground) {
        if (playAudioInBackground) {
            notificationManager.setPlayer(player)
        } else {
            notificationManager.setPlayer(null)
        }
        onDispose {
            notificationManager.setPlayer(null)
        }
    }

    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }

    DisposableEffect(view, isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose {
            view.keepScreenOn = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }

            override fun onIsPlayingChanged(isPlayingChange: Boolean) {
                isPlaying = isPlayingChange
                updatePipParams(isPlayingChange)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Pause only in ON_STOP when not in PiP and not playing background audio.
    // Transitioning into PiP puts the activity into ON_PAUSE, so keeping playback during ON_PAUSE
    // prevents PiP from stuttering or stopping.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        val inPip = isInPip || activity?.isInPictureInPictureMode == true
        if (!playAudioInBackground && !inPip) {
            resumePositionMs = player.currentPosition.coerceAtLeast(0L)
            resumePlaying = player.playWhenReady
            player.pause()
        } else if (playAudioInBackground && !inPip) {
            // Nothing is shown any more: stop decoding the pictures and keep only the sound (saves battery).
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_VIDEO)) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                .build()
        }
        val inPip = isInPip || activity?.isInPictureInPictureMode == true
        if (resumePlaying && !player.isPlaying && !inPip) {
            player.play()
        }
    }

    LaunchedEffect(unlockButtonVisible) {
        if (unlockButtonVisible) {
            delay(3.seconds)
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
        if (supportsPip && (activity != null)) {
            updatePipParams(player.isPlaying)
            val videoSize = player.videoSize
            val params = PictureInPictureParams.Builder().apply {
                if ((videoSize.width > 0) && (videoSize.height > 0)) {
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

    val gestureDetector = remember(player, isLocked, audioManager) {
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean {
                    dragMode = DragMode.None
                    dragStartX = e.x
                    dragStartY = e.y
                    gestureStartSeekMs = player.currentPosition
                    gestureStartVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (!isLocked && !isInPip) {
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
                    if (!isInPip) {
                        val pv = playerViewRef
                        if (pv != null) {
                            if (pv.isControllerFullyVisible || controlsVisible) {
                                pv.hideController()
                            } else {
                                pv.showController()
                            }
                            return true
                        }
                    }
                    return false
                }

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float,
                ): Boolean {
                    if (isLocked || isInPip) return false
                    val e1Pos = e1 ?: return false
                    val totalDx = e2.x - e1Pos.x
                    val totalDy = e2.y - e1Pos.y

                    if (dragMode == DragMode.None) {
                        val touchSlop = systemTouchSlop
                        if (abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy) * 1.2f) {
                            dragMode = DragMode.Seek
                        } else if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx) * 1.2f) {
                            dragMode = DragMode.Volume
                        }
                    }

                    when (dragMode) {
                        DragMode.Seek -> {
                            val deltaFraction = totalDx / screenWidthPx
                            val duration = player.duration.coerceAtLeast(0L)
                            val seekRangeMs = minOf(90_000L, duration.takeIf { it > 0 } ?: 90_000L)
                            val seekDeltaMs = (deltaFraction * seekRangeMs).toLong()
                            val targetMs = (gestureStartSeekMs + seekDeltaMs).coerceIn(0L, duration)
                            swipeSeekInfo = SwipeSeekInfo(targetMs, seekDeltaMs, duration)
                            return true
                        }
                        DragMode.Volume -> {
                            val deltaFraction = -totalDy / screenHeightPx
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val volumeDelta = (deltaFraction * maxVol * 1.2f).toInt()
                            val targetVol = (gestureStartVolume + volumeDelta).coerceIn(0, maxVol)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                            swipeVolumeInfo = SwipeVolumeInfo(targetVol, maxVol)
                            return true
                        }
                        DragMode.None -> {}
                    }
                    return false
                }
            },
        )
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
                    playerViewRef = this
                    useController = !isLocked && !isInPip
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    controllerShowTimeoutMs = ControlsTimeoutMs
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == View.VISIBLE && !isLocked
                        },
                    )
                    @android.annotation.SuppressLint("ClickableViewAccessibility")
                    setOnTouchListener { _, event ->
                        gestureDetector.onTouchEvent(event)
                        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                            if (dragMode == DragMode.Seek) {
                                swipeSeekInfo?.let { (targetMs) -> player.seekTo(targetMs) }
                                swipeSeekInfo = null
                            }
                            swipeVolumeInfo = null
                            dragMode = DragMode.None
                        }
                        true
                    }
                }
            },
            update = {
                playerViewRef = it
                it.player = player
                it.useController = !isLocked && !isInPip
                it.keepScreenOn = isPlaying
            },
            onRelease = {
                it.keepScreenOn = false
                it.player = null
                playerViewRef = null
            },
        )

        // Swipe Seek HUD
        AnimatedVisibility(
            visible = swipeSeekInfo != null && !isInPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            swipeSeekInfo?.let { (targetMs, deltaMs, durationMs) ->
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    tonalElevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (deltaMs >= 0) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${formatPlayerTime(targetMs)} / ${formatPlayerTime(durationMs)}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "[${if (deltaMs >= 0) "+" else ""}${deltaMs / 1000}s]",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Swipe Volume HUD
        AnimatedVisibility(
            visible = swipeVolumeInfo != null && !isInPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            swipeVolumeInfo?.let { (volume, maxVolume) ->
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    tonalElevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = when {
                                volume == 0 -> Icons.AutoMirrored.Outlined.VolumeOff
                                volume < maxVolume / 2 -> Icons.AutoMirrored.Outlined.VolumeDown
                                else -> Icons.AutoMirrored.Outlined.VolumeUp
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${if (maxVolume > 0) (volume * 100 / maxVolume) else 0}%",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

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
                IconButton(
                    onClick = {
                        isLocked = true
                        controlsVisible = false
                    },
                ) {
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

/** Formats playback timestamp into m:ss or h:mm:ss. */
private fun formatPlayerTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/** Helper to extract Activity from Context. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
