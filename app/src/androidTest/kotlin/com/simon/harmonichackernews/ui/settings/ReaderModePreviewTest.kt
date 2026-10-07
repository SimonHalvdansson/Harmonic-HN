package com.simon.harmonichackernews.ui.settings

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.ReaderLineHeight
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual preview browser and its Compose height bridge without editing app settings. */
@RunWith(AndroidJUnit4::class)
class ReaderModePreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun previewLoadsThenAnimatesLineHeightAndResizesItsHostInBothDirections() {
        val store = InMemoryKeyValueStore()
        val reading = mutableStateOf(AppSettingsRepository(store, store.changes).snapshot().reading.copy(
            readerModeFontSize = 20,
            readerModeLineHeight = ReaderLineHeight.COMPACT,
        ))
        val cardHeights = mutableListOf<Int>()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.onSizeChanged { cardHeights += it.height }) {
                        AndroidReaderModePreview(reading.value)
                    }
                }
            }
        }
        compose.waitUntil(20_000) {
            var browser: WebView? = null
            compose.runOnUiThread { browser = findWebView(compose.activity.window.decorView) }
            browser?.let {
                evaluate(it, "document.readyState === 'complete' && document.fonts.status === 'loaded' && !!document.querySelector('p')") == "true"
            } ?: false
        }
        lateinit var browser: WebView
        compose.runOnUiThread { browser = findWebView(compose.activity.window.decorView)!! }
        assertEquals(28.0, evaluate(browser, "parseFloat(getComputedStyle(document.querySelector('p')).lineHeight)").toDouble(), 0.1)
        val loadedHeight = evaluate(browser, "document.getElementById('preview').getBoundingClientRect().height").toDouble()
        compose.waitUntil(5_000) {
            var settled = false
            compose.runOnUiThread {
                settled = kotlin.math.abs(browser.height - loadedHeight * browser.resources.displayMetrics.density) < 3
            }
            settled
        }
        compose.runOnUiThread {
            val initial = cardHeights.first()
            val final = cardHeights.last()
            assertTrue("Blank card should resize through intermediate heights: $cardHeights",
                cardHeights.any { it > minOf(initial, final) && it < maxOf(initial, final) })
        }

        for ((target, start, end) in listOf(
            Triple(ReaderLineHeight.RELAXED, 28.0, 40.0),
            Triple(ReaderLineHeight.COMPACT, 40.0, 28.0),
        )) {
            evaluate(browser, """
                window.previewSamples = [];
                window.previewSamplingFinished = false;
                var startedAt = performance.now();
                function sample(now) {
                    previewSamples.push([
                        parseFloat(getComputedStyle(document.querySelector('p')).lineHeight),
                        document.getElementById('preview').getBoundingClientRect().height
                    ]);
                    if (now - startedAt < 700) requestAnimationFrame(sample);
                    else window.previewSamplingFinished = true;
                }
                requestAnimationFrame(sample);
            """.trimIndent())
            compose.runOnUiThread { reading.value = reading.value.copy(readerModeLineHeight = target) }
            compose.waitUntil(5_000) { evaluate(browser, "window.previewSamplingFinished") == "true" }
            val samples = JSONArray(evaluate(browser, "window.previewSamples"))
            val lineHeights = (0 until samples.length()).map { samples.getJSONArray(it).getDouble(0) }
            val heights = (0 until samples.length()).map { samples.getJSONArray(it).getDouble(1) }
            assertTrue("Expected intermediate line spacing: $lineHeights",
                lineHeights.any { it > minOf(start, end) + 0.1 && it < maxOf(start, end) - 0.1 })
            assertTrue("The document must resize throughout the animation", heights.distinct().size > 2)
            assertEquals(end, lineHeights.last(), 0.1)
            compose.runOnUiThread {
                assertEquals(heights.last() * browser.resources.displayMetrics.density, browser.height.toDouble(), 3.0)
            }
        }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun evaluate(browser: WebView, script: String): String {
        val completed = CountDownLatch(1)
        val result = AtomicReference<String>()
        compose.runOnUiThread {
            browser.evaluateJavascript(script) { result.set(it); completed.countDown() }
        }
        assertTrue("JavaScript did not finish", completed.await(5, TimeUnit.SECONDS))
        return result.get()
    }
}
