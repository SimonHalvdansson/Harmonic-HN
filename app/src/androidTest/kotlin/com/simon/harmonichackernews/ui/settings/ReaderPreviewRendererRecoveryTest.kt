package com.simon.harmonichackernews.ui.settings

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ReaderPreviewRendererRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendererLossRecreatesThePreviewAndKeepsTheHostAlive() {
        val store = InMemoryKeyValueStore()
        val reading = AppSettingsRepository(store, store.changes).snapshot().reading
        compose.setContent { MaterialTheme { AndroidReaderModePreview(reading) } }
        var previous: WebView? = null
        repeat(3) { generation ->
            var browser: WebView? = null
            compose.waitUntil(20_000) {
                var ready = false
                compose.runOnUiThread {
                    browser = findBrowser(compose.activity.window.decorView)
                    ready = browser != null && browser !== previous &&
                        browser?.webViewRenderProcess != null && browser?.progress == 100
                }
                ready
            }
            compose.runOnUiThread {
                assertFalse(compose.activity.isDestroyed)
                assertNotSame(previous, browser)
                assertTrue(browser!!.tag is String)
            }
            val contentLoaded = CountDownLatch(1)
            compose.runOnUiThread {
                browser!!.evaluateJavascript(
                    "document.querySelector('#preview h1')?.textContent === 'Article preview'",
                ) { if (it == "true") contentLoaded.countDown() }
            }
            assertTrue("The recreated preview must load its document", contentLoaded.await(5, TimeUnit.SECONDS))
            if (generation < 2) compose.runOnUiThread {
                assertTrue(browser!!.webViewRenderProcess!!.terminate())
            }
            previous = browser
        }
    }

    private fun findBrowser(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findBrowser(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
