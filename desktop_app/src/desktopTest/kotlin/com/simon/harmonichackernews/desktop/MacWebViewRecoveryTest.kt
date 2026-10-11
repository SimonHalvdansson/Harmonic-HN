package com.simon.harmonichackernews.desktop

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

/** Opt-in native bridge coverage; requires the packaged native resource and an AWT display. */
class MacWebViewRecoveryTest {
    @Test
    fun unavailableFirstPageReportsFailureAndRetryLoadsTheArticle() {
        assumeTrue(System.getProperty("harmonic.testMacWebView") == "true")
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        val url = "http://127.0.0.1:$port/article"
        val snapshot = AtomicReference<DesktopBrowserSnapshot?>()
        val error = AtomicReference<Throwable?>()
        var canvas: MacWkWebViewCanvas? = null
        var frame: JFrame? = null
        val session = DesktopCommentsWebViewSession(url)
        var server: HttpServer? = null
        try {
            SwingUtilities.invokeAndWait {
                val browser = MacWkWebViewCanvas(
                    onStateChanged = { host, state -> session.updateFromBrowser(host, state); snapshot.set(state) },
                    onError = error::set,
                )
                canvas = browser
                frame = JFrame("Harmonic browser recovery test").apply {
                    add(browser)
                    setSize(600, 400)
                    isVisible = true
                }
                browser.setBrowserVisible(true)
                session.attach(browser)
            }
            await { error.get()?.let { throw it }; snapshot.get()?.loadError != null }
            SwingUtilities.invokeAndWait {
                assertFalse(session.isLoading)
                assertEquals(url, session.currentUrl())
                assertTrue(!session.loadError.isNullOrBlank())
            }
            server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0).apply {
                createContext("/article") { exchange ->
                    val bytes = "<html><title>Recovered article</title><body>Recovery succeeded</body></html>".toByteArray()
                    exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
                start()
            }
            SwingUtilities.invokeAndWait { session.reload() }
            await { snapshot.get()?.let { it.title == "Recovered article" && !it.isLoading && it.loadError == null } == true }
            SwingUtilities.invokeAndWait {
                assertFalse(session.isLoading)
                assertEquals(null, session.loadError)
                assertTrue(session.hasLoadedContent)
            }
        } finally {
            server?.stop(0)
            SwingUtilities.invokeAndWait {
                canvas?.let { session.detach(it); it.disposeBrowser() }
                frame?.dispose()
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 20_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Native browser did not reach the expected state" }
            Thread.sleep(25)
        }
    }
}
