package com.simon.harmonichackernews.platform

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Color
import androidx.browser.customtabs.CustomTabsIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidCustomTabLaunchTest {
    private val application = RuntimeEnvironment.getApplication()
    private val currentActivity = AndroidCurrentActivity(application)

    @Test
    fun activityCustomTabStaysInTheCallersTaskAndPreservesOptions() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val intent = AndroidExternalLinkLauncher.createCustomTabsIntent(
                controller.get(), shareable = true, toolbarColor = Color.RED,
            ).intent
            assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
            assertTrue(intent.hasExtra(CustomTabsIntent.EXTRA_SESSION))
            assertEquals(CustomTabsIntent.SHARE_STATE_ON, intent.getIntExtra(CustomTabsIntent.EXTRA_SHARE_STATE, -1))
            assertEquals(Color.RED, intent.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, 0))
        }
    }

    @Test
    fun applicationContextCustomTabHasTheRequiredNewTaskFlag() {
        val intent = AndroidExternalLinkLauncher.createCustomTabsIntent(
            application, shareable = false, toolbarColor = Color.BLACK,
        ).intent
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.hasExtra(CustomTabsIntent.EXTRA_SESSION))
        assertEquals(CustomTabsIntent.SHARE_STATE_OFF, intent.getIntExtra(CustomTabsIntent.EXTRA_SHARE_STATE, -1))
    }

    @Test
    fun activityIsAvailableOnlyWhileResumed() {
        assertNull(currentActivity.get())
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            assertSame(controller.get(), currentActivity.get())
            controller.pause()
            assertNull(currentActivity.get())
            controller.resume()
            assertSame(controller.get(), currentActivity.get())
        }
        assertNull(currentActivity.get())
    }

    @Test
    fun recreationUsesTheReplacementActivity() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val original = controller.get()
            controller.recreate()
            assertFalse(original === currentActivity.get())
            assertSame(controller.get(), currentActivity.get())
        }
        assertNull(currentActivity.get())
    }

    @Test
    fun finishingActivityIsNotUsed() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            controller.get().finish()
            assertNull(currentActivity.get())
        }
    }

    @Test
    fun olderActivityCallbacksDoNotClearTheCurrentActivity() {
        Robolectric.buildActivity(Activity::class.java).setup().use { first ->
            Robolectric.buildActivity(Activity::class.java).setup().use { second ->
                first.pause()
                currentActivity.onActivityDestroyed(first.get())
                assertSame(second.get(), currentActivity.get())
            }
        }
    }
}
