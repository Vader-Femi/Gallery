package com.femi.gallery.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.femi.gallery.presentation.settings.ThemePreferences
import java.util.concurrent.TimeUnit

/**
 * Puts the background scan job in the system's queue (or takes it out, when the person turned it off).
 *
 * Two jobs share one worker: one to run as soon as its conditions hold, so that scans that were running
 * when the app was closed carry on, and one a day, so that new photos are read even if the app is not opened.
 * Both wait for the battery not to be low, and for the charger when the person chose that.
 */
object BackgroundScans {
    private const val NOW = "library-scan-now"
    private const val DAILY = "library-scan-daily"

    /**
     * Brings the queue in line with the settings. With [settingsChanged] the jobs are replaced so their
     * conditions match the new settings; otherwise a job that is already queued is left alone.
     */
    fun update(context: Context, settingsChanged: Boolean = false) {
        val work = WorkManager.getInstance(context.applicationContext)
        if (!ThemePreferences.getBackgroundScan(context)) {
            work.cancelUniqueWork(NOW)
            work.cancelUniqueWork(DAILY)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(ThemePreferences.getScanOnlyWhileCharging(context))
            .build()

        work.enqueueUniqueWork(
            NOW,
            if (settingsChanged) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ScanWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build(),
        )
        work.enqueueUniquePeriodicWork(
            DAILY,
            if (settingsChanged) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ScanWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build(),
        )
    }
}
