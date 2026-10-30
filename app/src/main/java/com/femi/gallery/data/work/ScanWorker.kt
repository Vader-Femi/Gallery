package com.femi.gallery.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.femi.gallery.MainActivity
import com.femi.gallery.R
import com.femi.gallery.domain.scan.ProgressThrottle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

/**
 * The background job that keeps the library scans going when the app is not on screen. The system may
 * run it at any time its conditions hold (see [BackgroundScans]) and ends it after about ten minutes, so
 * it gives up a little earlier and asks to be run again; the scans save their progress as they go, so the
 * next run carries on from there.
 */
class ScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun runner(): BackgroundScanRunner
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "ScanWorker started in background")
        val runner = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).runner()

        // Android 12 and later only let an app start a foreground service while it is visible (or in a few other
        // cases). The first run, started when the app was opened, gets the notification; a run the system started
        // by itself while the app was closed cannot, and simply works without one (within its ten minutes).
        var foregroundAllowed = showForeground(getForegroundInfo())

        val throttle = ProgressThrottle()
        return when (
            runner.run(RUN_LIMIT_MS) { scanned, total ->
                if (foregroundAllowed && throttle.shouldUpdate(scanned, total, SystemClock.elapsedRealtime())) {
                    foregroundAllowed = showForeground(getForegroundInfo(scanned, total))
                }
            }
        ) {
            BackgroundScanRunner.Outcome.Finished -> Result.success()
            BackgroundScanRunner.Outcome.TimeUp -> Result.retry()
        }
    }

    /** Shows [info] as the foreground notification. False when the system refuses (see [doWork]); cancellation passes through. */
    private suspend fun showForeground(info: ForegroundInfo): Boolean = try {
        setForeground(info)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "No foreground notification for this run", e)
        false
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = getForegroundInfo(scanned = 0, total = 0)

    private fun getForegroundInfo(scanned: Int, total: Int): ForegroundInfo {
        createNotificationChannel()
        // The app's own launcher intent: it brings the app back to where it was instead of starting it over.
        val launch = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
            ?: Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = if (total > 0) {
            applicationContext.getString(R.string.notification_background_scan_progress, scanned, total)
        } else {
            applicationContext.getString(R.string.notification_background_scan_text)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.notification_background_scan_title))
            .setContentText(text)
            .setProgress(total, scanned.coerceAtMost(total), total <= 0)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun createNotificationChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.notification_channel_background_scan),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager?.createNotificationChannel(channel)
    }

    private companion object {
        const val TAG = "ScanWorker"
        const val CHANNEL_ID = "library_scan_channel"
        const val NOTIFICATION_ID = 1001

        /** A little under the ten minutes the system allows. */
        const val RUN_LIMIT_MS = 8 * 60 * 1_000L
    }
}
