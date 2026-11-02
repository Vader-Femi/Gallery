@file:Suppress("unused")
package androidx.core.app
class NotificationCompat {
    companion object { const val PRIORITY_LOW = -1; const val CATEGORY_SERVICE = "service" }
    class Builder(context: android.content.Context, channelId: String) {
        fun setSmallIcon(i: Int) = this
        fun setContentTitle(s: CharSequence) = this
        fun setContentText(s: CharSequence) = this
        fun setProgress(max: Int, progress: Int, indeterminate: Boolean) = this
        fun setContentIntent(p: android.app.PendingIntent) = this
        fun setOngoing(b: Boolean) = this
        fun setOnlyAlertOnce(b: Boolean) = this
        fun setPriority(p: Int) = this
        fun setCategory(c: String) = this
        fun build(): android.app.Notification = throw NotImplementedError()
    }
}
