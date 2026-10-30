package com.simon.harmonichackernews

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class AndroidStartupUiTest {
    @Test
    fun storageFailuresKeepRecoveryUsableUntilRetryReachesReady() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        var attempts = 0
        var retries = 0
        var failures = 0
        var ready = false
        try {
            activity.runAfterStartup(
                prepare = {
                    if (++attempts <= 2) throw IOException("storage unavailable")
                },
                retryStartup = { retries++ },
                onFailure = { failures++ },
                onReady = { ready = true; activity.setContentView(TextView(activity).apply { text = "Ready" }) },
            )
            assertFalse(ready)
            assertEquals(1, failures)
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            assertTrue(content.descendants().filterIsInstance<TextView>().any {
                it.text.contains("Your saved data has been kept")
            })
            content.descendants().filterIsInstance<Button>().single { it.text == "Try again" }.performClick()
            assertFalse(ready)
            assertEquals(2, failures)
            val retry = content.descendants().filterIsInstance<Button>().single { it.text == "Try again" }
            assertTrue(retry.isEnabled)
            retry.performClick()
            assertTrue(ready)
            assertEquals(3, attempts)
            assertEquals(2, retries)
            assertTrue(content.descendants().filterIsInstance<TextView>().any { it.text == "Ready" })
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun activityCancellationDoesNotShowAStorageErrorOrContinueStartup() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            var failed = false
            var ready = false
            controller.get().runAfterStartup(
                prepare = { throw CancellationException("activity destroyed") },
                retryStartup = { fail("No retry was requested") },
                onFailure = { failed = true },
                onReady = { ready = true },
            )
            assertFalse(failed)
            assertFalse(ready)
            assertEquals(0, controller.get().findViewById<ViewGroup>(android.R.id.content).childCount)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun ViewGroup.descendants(): List<View> = (0 until childCount).flatMap { index ->
        val child = getChildAt(index)
        listOf(child) + if (child is ViewGroup) child.descendants() else emptyList()
    }
}
