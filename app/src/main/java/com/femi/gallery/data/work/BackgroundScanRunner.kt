package com.femi.gallery.data.work

import android.os.SystemClock
import com.femi.gallery.core.lifecycle.AppForeground
import com.femi.gallery.domain.repository.ColorIndexRepository
import com.femi.gallery.domain.repository.LabelIndexRepository
import com.femi.gallery.domain.repository.LocationRepository
import com.femi.gallery.domain.repository.TextIndexRepository
import com.femi.gallery.domain.scan.ScanWaitPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the library scans (places, text, colours, labels) for a background job and waits for them. The
 * scans themselves live in the app and keep their own progress; this only starts them, which does
 * nothing for a scan that is already running or has nothing new to read, and keeps the job alive for as
 * long as they work.
 */
@Singleton
class BackgroundScanRunner @Inject constructor(
    private val locations: LocationRepository,
    private val text: TextIndexRepository,
    private val colors: ColorIndexRepository,
    private val labels: LabelIndexRepository,
) {
    enum class Outcome {
        /** Every scan finished. */
        Finished,

        /** The time ran out with scans still working; they were stopped and will carry on in the next run. */
        TimeUp,
    }

    suspend fun run(limitMs: Long): Outcome {
        locations.startScan()
        text.startScan()
        colors.startScan()
        labels.startScan()

        val startedAt = SystemClock.elapsedRealtime()
        try {
            while (true) {
                val running = locations.isRunning || text.isRunning || colors.isRunning || labels.isRunning
                when (ScanWaitPlan.next(running, SystemClock.elapsedRealtime() - startedAt, limitMs)) {
                    ScanWaitPlan.Step.Done -> return Outcome.Finished
                    ScanWaitPlan.Step.GiveUp -> {
                        stopWhenNobodyIsWatching()
                        return Outcome.TimeUp
                    }
                    ScanWaitPlan.Step.Wait -> delay(POLL_MS)
                }
            }
        } catch (e: CancellationException) {
            // The system ended the job (low battery, the charger was unplugged, the person turned it off).
            stopWhenNobodyIsWatching()
            throw e
        }
    }

    /** A scan the person is watching in the app carries on; only scans that were left running in the background stop. */
    private fun stopWhenNobodyIsWatching() {
        if (AppForeground.isInForeground) return
        locations.stopScan()
        text.stopScan()
        colors.stopScan()
        labels.stopScan()
    }

    private companion object {
        const val POLL_MS = 1_000L
    }
}
