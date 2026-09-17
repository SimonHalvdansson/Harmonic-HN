package com.simon.harmonichackernews

import android.view.View
import android.webkit.WebChromeClient.CustomViewCallback
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentsWebViewFullscreenTest {
    @Test
    fun backRestoresBrowserAndNotifiesTheVideoOnce() = onMainThread {
        val browser = FrameLayout(context)
        val container = FrameLayout(context)
        val previousParent = FrameLayout(context)
        val video = View(context)
        previousParent.addView(video)
        val changes = mutableListOf<Boolean>()
        val fullscreen = CommentsWebViewFullscreen(changes::add)
        var hiddenCalls = 0
        fullscreen.bind(browser, container)
        fullscreen.show(video, CustomViewCallback {
            hiddenCalls++
            // WebChromeClient can echo the hide request back to the owner.
            fullscreen.hide(notifyCallback = false)
        })

        assertTrue(fullscreen.isShowing)
        assertSame(container, video.parent)
        assertEquals(0, previousParent.childCount)
        assertEquals(View.GONE, browser.visibility)
        assertEquals(View.VISIBLE, container.visibility)

        fullscreen.hide(notifyCallback = true)
        fullscreen.hide(notifyCallback = true)

        assertFalse(fullscreen.isShowing)
        assertNull(video.parent)
        assertEquals(View.VISIBLE, browser.visibility)
        assertEquals(View.GONE, container.visibility)
        assertEquals(1, hiddenCalls)
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun replacingAndReleasingVideoRestoresContainersWithoutCallingDestroyedWebView() = onMainThread {
        val browser = FrameLayout(context)
        val container = FrameLayout(context)
        val firstVideo = View(context)
        val secondVideo = View(context)
        val fullscreen = CommentsWebViewFullscreen {}
        var firstHiddenCalls = 0
        var secondHiddenCalls = 0
        fullscreen.bind(browser, container)
        fullscreen.show(firstVideo, CustomViewCallback { firstHiddenCalls++ })
        fullscreen.show(secondVideo, CustomViewCallback { secondHiddenCalls++ })

        assertNull(firstVideo.parent)
        assertSame(container, secondVideo.parent)
        assertEquals(1, firstHiddenCalls)

        fullscreen.release()

        assertFalse(fullscreen.isShowing)
        assertNull(secondVideo.parent)
        assertEquals(0, container.childCount)
        assertEquals(View.VISIBLE, browser.visibility)
        assertEquals(View.GONE, container.visibility)
        assertEquals(0, secondHiddenCalls)
    }

    @Test
    fun requestAfterReleaseIsRejectedWithoutEnteringFullscreen() = onMainThread {
        val changes = mutableListOf<Boolean>()
        val fullscreen = CommentsWebViewFullscreen(changes::add)
        fullscreen.bind(FrameLayout(context), FrameLayout(context))
        fullscreen.release()
        var hiddenCalls = 0
        val video = View(context)

        fullscreen.show(video, CustomViewCallback { hiddenCalls++ })

        assertEquals(1, hiddenCalls)
        assertNull(video.parent)
        assertFalse(fullscreen.isShowing)
        assertTrue(changes.isEmpty())
    }

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun onMainThread(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }
}
