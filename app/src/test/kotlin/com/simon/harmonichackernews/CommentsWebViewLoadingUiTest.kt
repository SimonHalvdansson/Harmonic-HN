package com.simon.harmonichackernews

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.View
import com.simon.harmonichackernews.ui.comments.WebContentOverlayState
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CommentsWebViewLoadingUiTest {
    @Test
    fun anUnattachedBrowserCanBeMeasuredWithoutAComposeLifecycleOwner() {
        val context = android.view.ContextThemeWrapper(
            org.robolectric.RuntimeEnvironment.getApplication(),
            R.style.AppThemeMaterialFixedLight,
        )
        val host = CommentsWebViewHost(context)
        host.overlayState.beginLoad()
        host.root.measure(
            View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
        )
        host.root.layout(0, 0, 480, 800)
        assertEquals(480, host.webViewContainer.measuredWidth)
        assertTrue(host.overlayState.progressVisible)
    }

    @Test
    fun completionCancelsDelayedBackdropAndSettlesComposeState() {
        Robolectric.buildActivity(Activity::class.java).setup().use { activity ->
            val backdrop = View(activity.get())
            activity.get().setContentView(backdrop)
            val state = WebContentOverlayState()
            val loading = CommentsWebViewLoadingUi()
            loading.bind(state, backdrop)
            loading.beginLoad()
            loading.updateProgress(60)
            assertTrue(state.progressVisible)
            assertEquals(60, state.progress)
            assertEquals(0f, backdrop.alpha)
            loading.finishLoad(completeProgress = true)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
            assertFalse(state.progressVisible)
            assertEquals(100, state.progress)
            assertEquals(View.GONE, backdrop.visibility)
            assertEquals(0f, backdrop.alpha)
        }
    }

    @Test
    fun rebindingAndReleasingCannotLeaveAnOldLoadOrDownloadAlive() {
        Robolectric.buildActivity(Activity::class.java).setup().use { activity ->
            val backdrop = View(activity.get())
            activity.get().setContentView(backdrop)
            val old = WebContentOverlayState()
            val current = WebContentOverlayState()
            val loading = CommentsWebViewLoadingUi()
            loading.bind(old, backdrop)
            loading.beginLoad()
            old.showDownload { error("Disposed host action") }
            loading.bind(current, backdrop)
            assertFalse(old.progressVisible)
            assertNull(old.onDownload)
            loading.beginLoad()
            loading.release()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
            assertFalse(loading.isBound)
            assertFalse(current.progressVisible)
            assertEquals(0f, backdrop.alpha)
        }
    }
}
