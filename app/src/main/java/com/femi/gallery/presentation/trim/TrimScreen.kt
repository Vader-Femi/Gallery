package com.femi.gallery.presentation.trim

import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.femi.gallery.R
import com.femi.gallery.domain.video.TrimFailure
import com.femi.gallery.domain.video.TrimMath
import kotlinx.coroutines.delay

/**
 * Trims a video: a preview that plays only the kept part, a bar with two handles, and "Save trimmed copy", which
 * exports the part as a new video next to the original (the original is never changed).
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun TrimScreen(
    onClose: () -> Unit,
    viewModel: TrimViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exporting = state.export is TrimExport.Running

    val player = remember(viewModel.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(viewModel.uri))
            playWhenReady = false
            prepare()
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && player.duration != androidx.media3.common.C.TIME_UNSET) {
                    viewModel.onDuration(player.duration)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // The preview plays only the kept part: at the end handle it stops and goes back to the start handle.
    val range by rememberUpdatedState(state.range)
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            if (player.isPlaying && state.isReady && positionMs >= range.endMs) {
                player.pause()
                player.seekTo(range.startMs)
            }
            delay(PositionIntervalMs)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }

    // An export runs in this screen, so the phone must not fall asleep while it works.
    val view = LocalView.current
    DisposableEffect(exporting) {
        view.keepScreenOn = exporting
        onDispose { view.keepScreenOn = false }
    }

    // The result of an export.
    LaunchedEffect(state.export) {
        val export = state.export
        if (export is TrimExport.Done) {
            Toast.makeText(context, context.getString(R.string.trim_done, export.name), Toast.LENGTH_LONG).show()
            onClose()
        }
    }
    (state.export as? TrimExport.Failed)?.let { failed ->
        AlertDialog(
            onDismissRequest = viewModel::dismissFailure,
            title = { Text(stringResource(R.string.trim_failed_title)) },
            text = { Text(stringResource(failureMessage(failed.reason))) },
            confirmButton = { TextButton(onClick = viewModel::dismissFailure) { Text(stringResource(R.string.trim_ok)) } },
        )
    }

    BackHandler(enabled = exporting) { viewModel.cancel() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (exporting) viewModel.cancel() else onClose() }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.trim_close))
                }
                Text(
                    text = stringResource(R.string.trim_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        (LayoutInflater.from(ctx).inflate(R.layout.viewer_player_view, null, false) as PlayerView).apply {
                            this.player = player
                        }
                    },
                    update = { it.player = player },
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.isReady) {
                    TrimRangeBar(
                        durationMs = state.durationMs,
                        range = state.range,
                        positionMs = positionMs,
                        onStartChange = { ms ->
                            viewModel.setStart(ms)
                            player.seekTo(ms.coerceIn(0L, state.durationMs))
                        },
                        onEndChange = { ms ->
                            viewModel.setEnd(ms)
                            player.seekTo(ms.coerceIn(0L, state.durationMs))
                        },
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(TrimMath.format(state.range.startMs, tenths = true), style = MaterialTheme.typography.labelLarge)
                        Text(
                            stringResource(R.string.trim_length, TrimMath.format(state.range.lengthMs, tenths = true)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(TrimMath.format(state.range.endMs, tenths = true), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FilledIconButton(
                        enabled = state.isReady && !exporting,
                        onClick = {
                            if (isPlaying) {
                                player.pause()
                            } else {
                                // From the start handle when the position is outside the kept part.
                                if (player.currentPosition !in state.range.startMs until state.range.endMs) {
                                    player.seekTo(state.range.startMs)
                                }
                                player.play()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.trim_pause else R.string.trim_play),
                        )
                    }
                    Button(
                        enabled = state.canSave,
                        onClick = {
                            player.pause()
                            viewModel.save()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.trim_save)) }
                }
            }
        }

        if (state.export is TrimExport.Running) {
            ExportProgress(percent = (state.export as TrimExport.Running).percent, onCancel = viewModel::cancel)
        }
    }
}

@Composable
private fun ExportProgress(percent: Int, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.trim_exporting, percent)) },
        text = {
            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.picker_cancel)) } },
    )
}

private fun failureMessage(reason: TrimFailure): Int = when (reason) {
    TrimFailure.SourceUnreadable -> R.string.trim_failed_source
    TrimFailure.NotEnoughSpace -> R.string.trim_failed_space
    TrimFailure.Unsupported -> R.string.trim_failed_unsupported
    TrimFailure.Other -> R.string.trim_failed_other
}

private const val PositionIntervalMs = 50L
