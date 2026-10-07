package com.simon.harmonichackernews.desktop

import com.simon.harmonichackernews.presentation.ReaderModeScriptProtocol
import com.simon.harmonichackernews.presentation.ReaderModeTheme
import com.simon.harmonichackernews.ui.reader.ReaderModeResources
import com.simon.harmonichackernews.ui.reader.ReaderPreviewDocument
import kotlinx.coroutines.runBlocking
import java.awt.EventQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import kotlin.test.*

/** Opt in with HARMONIC_NATIVE_BROWSER_TESTS=1 on a Windows machine with WebView2 installed. */
class DesktopNativeReaderTest {
    @Test
    fun edgeRendersReaderAndPreviewUsingRealBrowserCss() {
        if (System.getenv("HARMONIC_NATIVE_BROWSER_TESTS") != "1" ||
            desktopEmbeddedBrowserBackend() != DesktopEmbeddedBrowserBackend.WINDOWS_EDGE) return
        val loaded = CompletableFuture<Unit>()
        lateinit var canvas: SwtEdgeBrowserCanvas
        lateinit var frame: JFrame
        EventQueue.invokeAndWait {
            canvas = SwtEdgeBrowserCanvas(
                { _, snapshot -> if (!snapshot.isLoading) loaded.complete(Unit) },
                { loaded.completeExceptionally(it) },
            )
            frame = JFrame("Harmonic reader verification").apply {
                setSize(480, 700)
                add(canvas)
                isVisible = true
            }
            canvas.loadHtml("""<!doctype html><title>Test article</title><article><h1>Native reader test</h1>
                <p>${"This is an article with meaningful sentences, words, and punctuation. ".repeat(40)}</p>
                <input id="draft" value="preserve me"></article>""")
        }
        fun evaluate(script: String): String? {
            val result = CompletableFuture<String?>()
            EventQueue.invokeLater { canvas.evaluateJavaScriptResult(script, result::complete) }
            return result.get(20, TimeUnit.SECONDS)
        }
        fun waitFor(script: String, expected: String) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (evaluate(script) != expected && System.nanoTime() < deadline) Thread.sleep(50)
            assertEquals(expected, evaluate(script))
        }
        try {
            loaded.get(30, TimeUnit.SECONDS)
            val source = runBlocking { ReaderModeResources.script() }
            assertEquals("available", evaluate(ReaderModeScriptProtocol.availabilityCommand(source)))
            val theme = ReaderModeTheme(true, "#fff", "#111", "#111", "#555", "#00f", "#ddd", "#eee",
                fontSizePx = 13, lineHeight = 1.4)
            assertEquals("enabled", evaluate(ReaderModeScriptProtocol.applyCommand(source, theme, true)))
            waitFor("String(!!document.querySelector('#harmonic-reader-article p'))", "true")
            assertEquals("13px/18.2px", evaluate("(function(){var s=getComputedStyle(document.querySelector('#harmonic-reader-article p'));return s.fontSize+'/'+s.lineHeight;})()"))
            assertEquals("disabled", evaluate(ReaderModeScriptProtocol.applyCommand(source, theme, false)))
            waitFor("String(!!document.getElementById('draft'))", "true")
            assertEquals("preserve me", evaluate("document.getElementById('draft').value"))
            val fontCss = runBlocking { ReaderModeResources.fontData("georgia")!!.fontFaceCss }
            val preview = ReaderPreviewDocument.html("georgia", fontCss, -1, -16777216, "document.title='ready';")
            EventQueue.invokeAndWait { canvas.loadHtml(preview) }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (evaluate("document.title") != "ready" && System.nanoTime() < deadline) Thread.sleep(50)
            assertEquals("ready", evaluate("document.title"))
            evaluate(ReaderPreviewDocument.sizeScript(17, 2.0) + "\n'updated'")
            assertEquals("17px/34px", evaluate("(function(){var s=getComputedStyle(document.querySelector('p'));return s.fontSize+'/'+s.lineHeight;})()"))
            assertEquals("true", evaluate("String(getComputedStyle(document.querySelector('p')).fontFamily.includes('HarmonicReaderFont'))"))
        } finally {
            EventQueue.invokeAndWait { canvas.disposeBrowser(); frame.dispose() }
        }
    }
}
