package com.femi.gallery.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
        val runner = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).runner()
        return when (runner.run(RUN_LIMIT_MS)) {
            BackgroundScanRunner.Outcome.Finished -> Result.success()
            BackgroundScanRunner.Outcome.TimeUp -> Result.retry()
        }
    }

    private companion object {
        /** A little under the ten minutes the system allows. */
        const val RUN_LIMIT_MS = 8 * 60 * 1_000L
    }
}
