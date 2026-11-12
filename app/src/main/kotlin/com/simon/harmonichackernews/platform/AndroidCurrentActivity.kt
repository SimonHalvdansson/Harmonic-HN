package com.simon.harmonichackernews.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/** Registered on Main before startup work, so even the first resume is observed. */
internal class AndroidCurrentActivity(application: Application) : Application.ActivityLifecycleCallbacks {
    private var resumedActivity = WeakReference<Activity>(null)

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    fun get(): Activity? = resumedActivity.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    override fun onActivityResumed(activity: Activity) {
        resumedActivity = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) = clear(activity)

    override fun onActivityDestroyed(activity: Activity) = clear(activity)

    private fun clear(activity: Activity) {
        if (resumedActivity.get() === activity) resumedActivity.clear()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
