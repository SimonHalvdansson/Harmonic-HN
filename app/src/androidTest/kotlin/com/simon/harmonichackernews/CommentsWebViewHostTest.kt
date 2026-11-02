package com.simon.harmonichackernews

import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.children
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentsWebViewHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun awaitApplicationStartup() = runBlocking {
        InstrumentationRegistry.getInstrumentation().targetContext.awaitHarmonicAppComposition()
        Unit
    }

    @Test
    fun commentsOnlyHostDoesNotCreateAnOverlayComposition() {
        val (host, _) = attachHost()
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(host.root.isAttachedToWindow)
            assertTrue(host.webViewContainer.children.none { it is ComposeView })
        }
    }

    @Test
    fun activatedOverlayRendersPendingStateAndSurvivesReattachment() {
        val (host, parent) = attachHost()
        var downloads = 0
        compose.runOnIdle {
            host.overlayState.beginLoad()
            host.overlayState.updateProgress(40)
            host.ensureOverlay()
            host.ensureOverlay()
        }
        compose.onNodeWithTag("webview_progress").assertExists()
        val overlay = compose.runOnIdle {
            host.webViewContainer.children.filterIsInstance<ComposeView>().single()
        }
        compose.runOnIdle {
            host.overlayState.finishLoad(completeProgress = true)
            host.overlayState.showDownload { downloads++ }
        }
        compose.onNodeWithTag("webview_progress").assertDoesNotExist()
        compose.onNodeWithTag("webview_download").performClick()
        compose.runOnIdle {
            assertEquals(1, downloads)
            parent.removeView(host.root)
        }
        compose.runOnIdle {
            assertEquals(View.GONE, overlay.visibility)
            parent.addView(host.root)
            host.ensureOverlay()
        }
        compose.onNodeWithTag("webview_download").performClick()
        compose.runOnIdle {
            assertEquals(2, downloads)
            assertSame(overlay, host.webViewContainer.children.filterIsInstance<ComposeView>().single())
            assertEquals(View.VISIBLE, overlay.visibility)
        }
    }

    private fun attachHost(): Pair<CommentsWebViewHost, FrameLayout> {
        val host = compose.runOnUiThread { CommentsWebViewHost(compose.activity) }
        val parent = compose.runOnUiThread {
            FrameLayout(compose.activity).apply { addView(host.root) }
        }
        compose.setContent {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { parent })
        }
        return host to parent
    }
}
