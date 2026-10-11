package com.simon.harmonichackernews.desktop

import com.sun.jna.Pointer
import java.awt.EventQueue
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import javax.swing.SwingUtilities
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacWebViewDispatchTest {
    @Test fun appKitCallsAndTeardownLeaveTheAwtThreadAvailable() {
        assumeTrue(System.getProperty("harmonic.testMacWebView") == "true")
        val creating = CountDownLatch(1)
        val allowCreation = CountDownLatch(1)
        val loaded = CountDownLatch(1)
        val resized = CountDownLatch(1)
        val destroying = CountDownLatch(1)
        val allowDestruction = CountDownLatch(1)
        val destroyed = CountDownLatch(1)
        val error = AtomicReference<Throwable?>()
        val api = Proxy.newProxyInstance(MacWebViewApi::class.java.classLoader, arrayOf(MacWebViewApi::class.java)) { _, method, _ ->
            if (method.name.startsWith("harmonic_webview_") && method.name != "harmonic_webview_retain_view") {
                check(!EventQueue.isDispatchThread()) { "${method.name} must not wait for AppKit on AWT" }
            }
            when (method.name) {
                "harmonic_webview_create" -> {
                    creating.countDown()
                    check(allowCreation.await(5, TimeUnit.SECONDS))
                    Pointer(1)
                }
                "harmonic_webview_load_html" -> { loaded.countDown(); null }
                "harmonic_webview_set_frame" -> { resized.countDown(); null }
                "harmonic_webview_destroy" -> {
                    destroying.countDown()
                    check(allowDestruction.await(5, TimeUnit.SECONDS))
                    destroyed.countDown()
                    null
                }
                else -> null
            }
        } as MacWebViewApi
        var frame: JFrame? = null
        var canvas: MacWkWebViewCanvas? = null
        try {
            SwingUtilities.invokeAndWait {
                canvas = MacWkWebViewCanvas({ _, _ -> }, error::set, { api })
                frame = JFrame("Harmonic native dispatch test").apply {
                    add(canvas)
                    setSize(600, 400)
                    isVisible = true
                }
                canvas!!.loadHtml("<html><title>Queued before creation</title></html>")
            }
            assertTrue(creating.await(5, TimeUnit.SECONDS))
            // Simulate AppKit waiting for an accessibility answer from AWT during native creation.
            SwingUtilities.invokeAndWait { assertTrue(EventQueue.isDispatchThread()) }
            allowCreation.countDown()
            assertTrue(loaded.await(5, TimeUnit.SECONDS))
            SwingUtilities.invokeAndWait { frame!!.setSize(700, 500) }
            assertTrue(resized.await(5, TimeUnit.SECONDS))
            SwingUtilities.invokeAndWait { canvas!!.disposeBrowser(); frame!!.dispose() }
            assertTrue(destroying.await(5, TimeUnit.SECONDS))
            SwingUtilities.invokeAndWait { assertTrue(EventQueue.isDispatchThread()) }
            allowDestruction.countDown()
            assertTrue(destroyed.await(5, TimeUnit.SECONDS))
            assertNull(error.get())
        } finally {
            allowCreation.countDown()
            allowDestruction.countDown()
            SwingUtilities.invokeAndWait { canvas?.disposeBrowser(); frame?.dispose() }
        }
    }
}
