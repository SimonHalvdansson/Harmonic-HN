package com.simon.harmonichackernews.benchmark

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/** Measures synchronous availability + enable preparation in the emulator's real WebView.
 * Navigation, JavaScript installation, and the asynchronous visual transition are excluded. */
@RunWith(AndroidJUnit4::class)
class ReaderExtractionBenchmark {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun availabilityThenEnable() {
        val context = instrumentation.context.createPackageContext(BenchmarkPackageName, 0)
        val root = "composeResources/com.simon.harmonichackernews.resources/files/web/"
        val script = listOf("vendor/mozilla/readability/0.6.0/Readability.min.js", "reader_mode.js")
            .joinToString("\n") { context.assets.open(root + it).bufferedReader().use { reader -> reader.readText() } }
        val paragraph = "A substantive article sentence, with detail and punctuation. ".repeat(15)
        val html = "<!doctype html><title>Benchmark article</title><article><h1>Benchmark article</h1>" +
            (1..160).joinToString("") { "<p>Paragraph $it. $paragraph</p>" } + "</article>"
        lateinit var view: WebView
        instrumentation.runOnMainSync {
            view = WebView(context).apply { settings.javaScriptEnabled = true }
        }
        val samples = mutableListOf<Double>()
        val clones = mutableListOf<Int>()
        try {
            repeat(30) { iteration ->
                val ready = CountDownLatch(1)
                instrumentation.runOnMainSync {
                    view.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) { ready.countDown() }
                    }
                    view.loadDataWithBaseURL("https://reader-benchmark.test/article", html, "text/html", "UTF-8", null)
                }
                check(ready.await(20, TimeUnit.SECONDS)) { "WebView load timed out" }
                evaluate(view, script + "\nwindow.cloneCount=0; window.originalClone=Document.prototype.cloneNode;" +
                    "Document.prototype.cloneNode=function(deep){window.cloneCount++;return originalClone.call(this,deep);};")
                val result = JSONObject(evaluate(view, """
                    (function(){
                        var start=performance.now();
                        var available=HarmonicReaderMode.isAvailable();
                        var enabled=HarmonicReaderMode.enable();
                        return {ms:performance.now()-start,clones:cloneCount,available:available,enabled:enabled};
                    })()
                """))
                check(result.getString("available") == "available" && result.getString("enabled") == "enabled")
                if (iteration >= 5) {
                    samples += result.getDouble("ms")
                    clones += result.getInt("clones")
                }
            }
            instrumentation.sendStatus(2, Bundle().apply {
                putString("readerExtractionSamplesMs", samples.joinToString(","))
                putString("readerExtractionDocumentClones", clones.joinToString(","))
            })
        } finally {
            instrumentation.runOnMainSync { view.destroy() }
        }
    }

    private fun evaluate(view: WebView, script: String): String {
        val completed = CountDownLatch(1)
        var result = "null"
        instrumentation.runOnMainSync {
            view.evaluateJavascript(script) { value -> result = value; completed.countDown() }
        }
        check(completed.await(20, TimeUnit.SECONDS)) { "JavaScript evaluation timed out" }
        return result
    }
}
