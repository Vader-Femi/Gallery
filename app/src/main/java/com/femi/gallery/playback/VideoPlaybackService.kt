package com.femi.gallery.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.femi.gallery.R

/**
 * Foreground service hosting the active [MediaSession] for video background audio and PiP.
 *
 * Keeps playback active and displays system media player controls in notification shade and lock screen.
 */
@UnstableApi
class VideoPlaybackService : MediaSessionService() {

    companion object {
        @Volatile
        private var activeSession: MediaSession? = null
        private var retainedPlayer: Player? = null

        const val CHANNEL_ID = "gallery_video_playback"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_FOREGROUND = "com.femi.gallery.playback.action.START_FOREGROUND"
        const val ACTION_STOP = "com.femi.gallery.playback.action.STOP"
        const val ACTION_PLAY = "com.femi.gallery.playback.action.PLAY"
        const val ACTION_PAUSE = "com.femi.gallery.playback.action.PAUSE"

        fun setSession(session: MediaSession?) {
            activeSession = session
        }

        fun hasActivePlayback(): Boolean = activeSession?.player?.isPlaying == true

        fun getActivePlaybackUri(): String? {
            return activeSession?.player?.currentMediaItem?.mediaId
                ?: activeSession?.player?.currentMediaItem?.localConfiguration?.uri?.toString()
        }

        fun getActivePlaybackPosition(): Long? {
            return activeSession?.player?.currentPosition
        }

        fun startForegroundPlayback(context: Context, session: MediaSession, player: Player? = null) {
            activeSession = session
            if (player != null) {
                retainedPlayer = player
            }
            val intent = Intent(context, VideoPlaybackService::class.java).apply {
                action = ACTION_START_FOREGROUND
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                try {
                    context.startService(intent)
                } catch (_: Exception) {}
            }
        }

        fun stopForegroundPlayback(context: Context) {
            val intent = Intent(context, VideoPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
            retainedPlayer?.stop()
            retainedPlayer?.release()
            retainedPlayer = null
            activeSession?.release()
            activeSession = null
        }
    }

    private var isForeground = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isForeground) {
                updateNotification()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                activeSession?.let { session ->
                    session.player.removeListener(this)
                    if (isSessionAdded(session)) {
                        removeSession(session)
                    }
                }
                retainedPlayer?.release()
                retainedPlayer = null
                activeSession = null
                isForeground = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } else if (isForeground) {
                updateNotification()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return activeSession ?: sessions.firstOrNull()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOREGROUND -> {
                val session = activeSession
                if (session != null) {
                    session.player.removeListener(playerListener)
                    session.player.addListener(playerListener)
                    if (!isSessionAdded(session)) {
                        addSession(session)
                    }
                    startForegroundWithNotification(session)
                } else {
                    // Fallback shutdown notification so startForegroundService contract is never violated
                    ensureNotificationChannel()
                    val dummyNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_media_play)
                        .setContentTitle(getString(R.string.player_video_title))
                        .build()
                    startForeground(
                        NOTIFICATION_ID,
                        dummyNotification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                    )
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                activeSession?.let { session ->
                    session.player.removeListener(playerListener)
                    if (isSessionAdded(session)) {
                        removeSession(session)
                    }
                }
                retainedPlayer?.release()
                retainedPlayer = null
                activeSession = null
                isForeground = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_PLAY -> {
                activeSession?.player?.play()
                updateNotification()
            }
            ACTION_PAUSE -> {
                activeSession?.player?.pause()
                updateNotification()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // Handled directly via startForegroundWithNotification and updateNotification
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.player_video_title),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.player_channel_description)
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(channel)
    }

    private fun createPlaybackNotification(session: MediaSession): Notification {
        ensureNotificationChannel()
        val player = session.player
        val title = player.mediaMetadata.title?.toString()
            ?: getString(R.string.player_video_title)

        val isPlaying = player.isPlaying

        val playPauseIntent = Intent(this, VideoPlaybackService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            100,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, VideoPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val playPauseAction = NotificationCompat.Action.Builder(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            getString(if (isPlaying) R.string.player_pause else R.string.player_play),
            playPausePendingIntent,
        ).build()

        val mediaStyle = MediaStyleNotificationHelper.MediaStyle(session)
            .setShowActionsInCompactView(0)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(session.sessionActivity)
            .setDeleteIntent(stopPendingIntent)
            .setStyle(mediaStyle)
            .addAction(playPauseAction)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun startForegroundWithNotification(session: MediaSession) {
        val notification = createPlaybackNotification(session)
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        isForeground = true
    }

    private fun updateNotification() {
        activeSession?.let { session ->
            val notification = createPlaybackNotification(session)
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = activeSession?.player
        if ((player == null) || !player.playWhenReady || (player.mediaItemCount == 0)) {
            activeSession?.player?.removeListener(playerListener)
            retainedPlayer?.release()
            retainedPlayer = null
            activeSession = null
            isForeground = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        activeSession?.player?.removeListener(playerListener)
        retainedPlayer?.release()
        retainedPlayer = null
        activeSession = null
        isForeground = false
        super.onDestroy()
    }
}
