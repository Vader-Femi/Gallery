package com.femi.gallery.domain.scan

/**
 * What a background run does while the scans work: keep waiting until they are all finished, but not
 * longer than the limit the system allows a background job to run (after which the work is handed back
 * and picked up again later, from where the scans' last checkpoint left off).
 */
object ScanWaitPlan {

    enum class Step {
        /** Every scan is finished. */
        Done,

        /** Some scan is still working and there is time left. */
        Wait,

        /** Time is up with scans still working. */
        GiveUp,
    }

    fun next(anyScanRunning: Boolean, elapsedMs: Long, limitMs: Long): Step = when {
        !anyScanRunning -> Step.Done
        elapsedMs >= limitMs -> Step.GiveUp
        else -> Step.Wait
    }
}
