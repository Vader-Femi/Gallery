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

        @Volatile
        private var retainedPlayer: Player? = null

        /** True between the service's onCreate and onDestroy, so that nothing starts it just to stop it. */
        @Volatile
        private var serviceRunning = false

        const val CHANNEL_ID = "gallery_video_playback"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_FOREGROUND = "com.femi.gallery.playback.action.START_FOREGROUND"
        const val ACTION_STOP = "com.femi.gallery.playback.action.STOP"
        const val ACTION_PLAY = "com.femi.gallery.playback.action.PLAY"
        const val ACTION_PAUSE = "com.femi.gallery.playback.action.PAUSE"
        const val ACTION_PLAYBACK = "com.femi.gallery.playback.action.PLAYBACK"
        /** Which session a stop request is for; a request for a session that is no longer the served one is ignored. */
        const val EXTRA_SESSION_ID = "com.femi.gallery.playback.extra.SESSION_ID"
        const val EXTRA_PLAYBACK_NOTIFICATION = "com.femi.gallery.playback.extra.NOTIFICATION"

        fun createPlaybackPendingIntent(context: Context, uri: String): PendingIntent {
            val intent = Intent(context, com.femi.gallery.MainActivity::class.java).apply {
                action = ACTION_PLAYBACK
                setDataAndType(android.net.Uri.parse(uri), "video/*")
                putExtra(EXTRA_PLAYBACK_NOTIFICATION, true)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun setSession(session: MediaSession?) {
            activeSession = session
        }

        fun hasActivePlayback(): Boolean = activeSession?.player?.isPlaying == true

        fun getActivePlaybackUri(): String? {
            val item = activeSession?.player?.currentMediaItem
            // The media id is never null in Media3, it is "" when none was set, so the fallback needs an empty check.
            return item?.mediaId?.takeIf { it.isNotEmpty() } ?: item?.localConfiguration?.uri?.toString()
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
            val stoppedSession = activeSession
            val stoppedPlayer = retainedPlayer
            activeSession = null
            retainedPlayer = null
            if (serviceRunning) {
                // The session's id tells the service which session this is for: if another video has taken over by the
                // time the service handles it, the request must not stop the new one.
                val intent = Intent(context, VideoPlaybackService::class.java).apply {
                    action = ACTION_STOP
                    putExtra(EXTRA_SESSION_ID, stoppedSession?.id)
                }
                try {
                    context.startService(intent)
                } catch (_: Exception) {}
            }
            stoppedPlayer?.release()
            stoppedSession?.release()
        }
    }

    private var isForeground = false

    /** The session this service is showing the notification for; only this one is ever cleaned up here. */
    private var served: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        serviceRunning = true
    }

    /** Detaches the served session, and releases it and the player kept for it. */
    private fun releaseServed() {
        val session = served ?: return
        served = null
        isForeground = false
        runCatching { session.player.removeListener(playerListener) }
        if (isSessionAdded(session)) removeSession(session)
        if (activeSession === session) activeSession = null
        if (retainedPlayer === session.player) {
            retainedPlayer = null
            runCatching { session.player.release() }
        }
        session.release()
    }

    /** The one way playback ends here: ended, stopped from the notification, task removed. */
    private fun cleanup() {
        releaseServed()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isForeground) {
                updateNotification()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                cleanup()
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
                    if (served != null && served !== session) releaseServed()
                    served = session
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
                val target = intent?.getStringExtra(EXTRA_SESSION_ID)
                // From the notification there is no id and it stops what is shown; from the app, only its own session.
                if (target == null || served == null || served?.id == target) cleanup()
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

        val contentIntent = session.sessionActivity ?: run {
            val playbackUri = getActivePlaybackUri()
            if (playbackUri != null) {
                createPlaybackPendingIntent(this, playbackUri)
            } else {
                null
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
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
        val player = served?.player
        if ((player == null) || !player.playWhenReady || (player.mediaItemCount == 0)) {
            cleanup()
        }
    }

    override fun onDestroy() {
        releaseServed()
        serviceRunning = false
        super.onDestroy()
    }
}
