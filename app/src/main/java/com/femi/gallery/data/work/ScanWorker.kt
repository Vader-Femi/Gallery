package com.femi.gallery.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.femi.gallery.MainActivity
import com.femi.gallery.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

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
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set foreground service for ScanWorker", e)
        }
        val runner = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).runner()
        return when (
            runner.run(RUN_LIMIT_MS) { scanned, total ->
                try {
                    setForeground(getForegroundInfo("Indexing: $scanned / $total photos"))
                } catch (e: Exception) {
                    // Ignore foreground update failures
                }
            }
        ) {
            BackgroundScanRunner.Outcome.Finished -> Result.success()
            BackgroundScanRunner.Outcome.TimeUp -> Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        getForegroundInfo(applicationContext.getString(R.string.notification_background_scan_text))

    private fun getForegroundInfo(contentText: String): ForegroundInfo {
        createNotificationChannel()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.notification_background_scan_title))
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
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
