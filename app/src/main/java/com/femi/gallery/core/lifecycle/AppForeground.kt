package com.femi.gallery.core.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Whether one of the app's screens is on display. Background work asks it before stopping a scan, because
 * a scan that the person is watching in the app must not be ended just because the background job's time is up.
 */
object AppForeground {
    @Volatile
    private var startedActivities = 0

    val isInForeground: Boolean get() = startedActivities > 0

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    startedActivities++
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities = (startedActivities - 1).coerceAtLeast(0)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
