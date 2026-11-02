@file:Suppress("unused")
package androidx.work
class WorkerParameters
class ForegroundInfo(val id: Int, val notification: android.app.Notification, val type: Int)
abstract class ListenableWorker {
    abstract class Result {
        companion object { fun success(): Result = throw NotImplementedError(); fun retry(): Result = throw NotImplementedError() }
    }
}
abstract class CoroutineWorker(val applicationContext: android.content.Context, params: WorkerParameters) : ListenableWorker() {
    abstract suspend fun doWork(): Result
    open suspend fun getForegroundInfo(): ForegroundInfo = throw NotImplementedError()
    suspend fun setForeground(info: ForegroundInfo) {}
}
