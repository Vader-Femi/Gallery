@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.media3.session

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player

/** Stub for tools/verify, from androidx/media 1.11.1 (media3-session): only what the playback service uses. */
class MediaSession private constructor() {
    class ControllerInfo

    val id: String get() = throw NotImplementedError()
    val player: Player get() = throw NotImplementedError()
    val sessionActivity: PendingIntent? get() = throw NotImplementedError()
    fun release() {}
}

abstract class MediaSessionService : android.app.Service() {
    override fun onCreate() {}
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = 0
    override fun onTaskRemoved(rootIntent: Intent?) {}
    override fun onDestroy() {}
    override fun onBind(intent: Intent?): android.os.IBinder? = null

    abstract fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession?
    open fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {}

    fun addSession(session: MediaSession) {}
    fun removeSession(session: MediaSession) {}
    fun isSessionAdded(session: MediaSession): Boolean = false
    val sessions: List<MediaSession> get() = emptyList()
}

object MediaStyleNotificationHelper {
    class MediaStyle(session: MediaSession) : NotificationCompat.Style() {
        fun setShowActionsInCompactView(vararg actions: Int): MediaStyle = this
    }
}
