package com.simon.harmonichackernews

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentsOpeningWorkTest {
    @Test
    fun headerArrivingAfterFirstDrawStillDefersHiddenBrowserStartup() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val initialized = CountDownLatch(1)
            val headerAt = AtomicLong()
            val browserAt = AtomicLong()
            lateinit var work: CommentsOpeningWork
            lateinit var view: View
            scenario.onActivity { activity ->
                var integrated = false
                view = View(activity)
                (activity.window.decorView as ViewGroup).addView(view, ViewGroup.LayoutParams(1, 1))
                work = CommentsOpeningWork(
                    integrated = { integrated }, showingWebsite = { false },
                    hiddenBrowserDelayMillis = { 200L },
                    loadComments = {
                        // The same request made when asynchronous cached metadata enables links.
                        integrated = true
                        headerAt.set(SystemClock.uptimeMillis())
                        work.requestConfiguredBrowser()
                    },
                    initializeVisibleBrowser = {},
                    initializeConfiguredBrowser = {
                        browserAt.compareAndSet(0L, SystemClock.uptimeMillis())
                        initialized.countDown()
                    },
                    startSummary = {},
                )
                work.schedule(view)
            }
            try {
                assertTrue(initialized.await(5, TimeUnit.SECONDS))
                assertTrue("Hidden browser started during the protected opening interval",
                    browserAt.get() - headerAt.get() >= 200L)
            } finally {
                scenario.onActivity { work.close(); (view.parent as? ViewGroup)?.removeView(view) }
            }
        }
    }

    @Test
    fun leavingDuringHeaderRestorationCancelsDelayedBrowserStartup() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val left = CountDownLatch(1)
            val initialized = CountDownLatch(1)
            lateinit var work: CommentsOpeningWork
            lateinit var view: View
            scenario.onActivity { activity ->
                view = View(activity)
                (activity.window.decorView as ViewGroup).addView(view, ViewGroup.LayoutParams(1, 1))
                work = CommentsOpeningWork(
                    integrated = { true }, showingWebsite = { false },
                    hiddenBrowserDelayMillis = { 100L },
                    loadComments = { work.close(); left.countDown() },
                    initializeVisibleBrowser = { initialized.countDown() },
                    initializeConfiguredBrowser = { initialized.countDown() },
                    startSummary = {},
                )
                work.schedule(view)
            }
            try {
                assertTrue(left.await(5, TimeUnit.SECONDS))
                assertFalse(initialized.await(300, TimeUnit.MILLISECONDS))
            } finally {
                scenario.onActivity { work.close(); (view.parent as? ViewGroup)?.removeView(view) }
            }
        }
    }
}
