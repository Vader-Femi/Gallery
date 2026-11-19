@file:Suppress("unused")
package androidx.core.app
class NotificationCompat {
    companion object { const val PRIORITY_LOW = -1; const val CATEGORY_SERVICE = "service"; const val VISIBILITY_PUBLIC = 1 }
    open class Style
    class Action private constructor() {
        class Builder(icon: Int, title: CharSequence?, intent: android.app.PendingIntent?) {
            fun build(): Action = Action()
        }
    }
    class Builder(context: android.content.Context, channelId: String) {
        fun setSmallIcon(i: Int) = this
        fun setContentTitle(s: CharSequence) = this
        fun setContentText(s: CharSequence) = this
        fun setProgress(max: Int, progress: Int, indeterminate: Boolean) = this
        fun setContentIntent(p: android.app.PendingIntent?) = this
        fun setDeleteIntent(p: android.app.PendingIntent?) = this
        fun setStyle(s: Style?) = this
        fun addAction(a: Action) = this
        fun setVisibility(v: Int) = this
        fun setOngoing(b: Boolean) = this
        fun setOnlyAlertOnce(b: Boolean) = this
        fun setPriority(p: Int) = this
        fun setCategory(c: String) = this
        fun build(): android.app.Notification = throw NotImplementedError()
    }
}
