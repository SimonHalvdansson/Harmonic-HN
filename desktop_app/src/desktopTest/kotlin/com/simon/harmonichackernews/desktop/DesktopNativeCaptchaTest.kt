package com.simon.harmonichackernews.desktop

import com.simon.harmonichackernews.utils.HackerNewsCaptchaWebProtocol
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.eclipse.swt.SWT
import java.awt.Canvas
import java.awt.EventQueue
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JFrame
import kotlin.test.*

/**
 * Real-engine fixture, never a live HN submission or CAPTCHA bypass. macOS: -Dharmonic.testMacWebView=true;
 * Windows/Linux: HARMONIC_NATIVE_BROWSER_TESTS=1 with the native engine installed and a display.
 */
class DesktopNativeCaptchaTest {
    @Test
    fun nativeChallengeKeepsTrustedOriginReadsEscapedResponseAndBlocksNavigation() = verifyNativeBrowser(false)

    @Test
    fun htmlBootstrapAvoidsFeedCspAndReportsOnlyRequiredResourcePolicyFailures() = verifyNativeBrowser(true)

    private fun verifyNativeBrowser(forceHtmlBootstrap: Boolean) {
        val os = System.getProperty("os.name")
        val mac = os.startsWith("Mac", true)
        if (mac && System.getProperty("harmonic.testMacWebView") != "true") return
        if (!mac && System.getenv("HARMONIC_NATIVE_BROWSER_TESTS") != "1") return
        if (!mac && !os.startsWith("Windows", true) && !os.startsWith("Linux", true)) return
        val feedRequests = AtomicInteger()
        val server = if (mac && !forceHtmlBootstrap) null else HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                if (exchange.requestURI.path == "/") {
                    feedRequests.incrementAndGet()
                    exchange.responseHeaders.set("Content-Security-Policy", "default-src 'self'; script-src 'unsafe-inline'")
                }
                val bytes = "<!doctype html><title>Trusted bootstrap</title><p>Bootstrap</p>".toByteArray()
                exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        val baseUrl = if (server == null) "https://news.ycombinator.com/" else "http://127.0.0.1:${server.address.port}/"
        val origin = URI(baseUrl).let { "${it.scheme}://${it.rawAuthority}" }
        val session = DesktopCaptchaBrowserSession(FIXTURE, baseUrl,
            bootstrapUrl = "${baseUrl}captcha-bootstrap.html")
        val nativeError = AtomicReference<Throwable?>()
        val escaped = AtomicInteger()
        lateinit var canvas: Canvas
        lateinit var browser: DesktopBrowserHost
        lateinit var frame: JFrame
        EventQueue.invokeAndWait {
            val onState: (DesktopBrowserHost, DesktopBrowserSnapshot) -> Unit = session::update
            val policy: (String?, Boolean) -> Boolean = { url, main ->
                if (server == null) HackerNewsCaptchaWebProtocol.allowsNavigation(url, main)
                else url == "about:blank" || runCatching {
                    URI(url).let { "${it.scheme}://${it.rawAuthority}" == origin }
                }.getOrDefault(false)
            }
            val onEscape = { escaped.incrementAndGet(); Unit }
            canvas = if (mac) MacWkWebViewCanvas(onState, nativeError::set,
                navigationPolicy = policy, onEscape = onEscape)
            else SwtEdgeBrowserCanvas(onState, nativeError::set,
                browserStyle = if (os.startsWith("Linux", true)) SWT.WEBKIT else SWT.EDGE,
                navigationPolicy = policy, onEscape = onEscape)
            browser = canvas as DesktopBrowserHost
            frame = JFrame("Harmonic CAPTCHA native fixture").apply {
                add(canvas)
                setSize(440, 550)
                isVisible = true
            }
            browser.setBrowserVisible(true)
            session.attach(browser, usesBaseUrl = mac && !forceHtmlBootstrap)
        }
        fun evaluate(script: String): String? {
            val future = CompletableFuture<String?>()
            EventQueue.invokeLater { browser.evaluateJavaScriptResult(script, future::complete) }
            return future.get(15, TimeUnit.SECONDS)
        }
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (!session.ready && session.error == null && System.nanoTime() < deadline) {
                nativeError.get()?.let { throw it }
                runBlocking { session.poll() }
                Thread.sleep(50)
            }
            nativeError.get()?.let { throw it }
            assertTrue(session.ready, (session.error ?: "Native CAPTCHA fixture did not become ready") +
                ": " + evaluate("location.origin+' | '+location.href+' | '+document.title+' | '+window.__harmonicCaptchaStatus"))
            assertEquals(origin, evaluate("location.origin"))
            assertEquals("fixture-\"response\"-λ", runBlocking { session.readResponse() })
            assertEquals("fixture-\"response\"-λ", evaluate("document.getElementById('g-recaptcha-response').value"))
            assertEquals(0, feedRequests.get(), "The feed's inherited CSP must never host the generated document")
            System.getProperty("harmonic.captchaFixtureHoldMs")?.toLongOrNull()
                ?.coerceIn(0, 30_000)?.takeIf { it > 0 }?.let(Thread::sleep)
            if (System.getProperty("harmonic.captchaFixtureVerifyEscape") == "true") {
                assertEquals(1, escaped.get(), "Native focused browser must forward one Escape to its dialog")
            }
            evaluate("document.getElementById('escape').click(); 'attempted'")
            // The navigation delegate resolves asynchronously. Its rejected destination never loads.
            Thread.sleep(350)
            assertEquals(origin, evaluate("location.origin"))
            assertEquals("Harmonic CAPTCHA fixture", evaluate("document.title"))
            if (forceHtmlBootstrap) {
                assertEquals("text/html", evaluate("document.contentType"))
                evaluate("window.__fetch='pending';fetch('data:text/plain,CSP-free').then(function(r){return r.text();}).then(function(t){window.__fetch=t;}).catch(function(){window.__fetch='blocked';});'started'")
                val fetchDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (evaluate("window.__fetch") == "pending" && System.nanoTime() < fetchDeadline) Thread.sleep(50)
                assertEquals("CSP-free", evaluate("window.__fetch"))
                fun policy(directive: String, blocked: String) = evaluate(
                    "document.dispatchEvent(new SecurityPolicyViolationEvent('securitypolicyviolation',{disposition:'enforce',effectiveDirective:'$directive',blockedURI:'$blocked'}));'dispatched'",
                )
                policy("connect-src", "https://www.google.com/recaptcha/api2/test")
                policy("img-src", "https://www.gstatic.com/recaptcha/test.png")
                policy("script-src-elem", "https://example.invalid/unrelated.js")
                runBlocking { session.poll() }
                assertTrue(session.ready, "Unrelated policies and iframe XHR must not fail the top document")
                policy("script-src", "inline")
                assertEquals("true", evaluate("String(window.__harmonicCaptchaPolicyFailed)"))
                evaluate("window.__harmonicCaptchaPolicyFailed=false;window.__harmonicCaptchaStatus='ready';'reset fixture'")
                policy("frame-src", "https://recaptcha.google.com")
                // A late provider onload callback cannot mask a policy violation already observed.
                evaluate("window.__harmonicCaptchaStatus='ready';'ready'")
                runBlocking { session.poll() }
                assertFalse(session.ready)
                assertTrue(session.error.orEmpty().contains("blocked a required script or frame"))
                assertNull(runBlocking { session.readResponse() })
            } else {
                evaluate("window.__harmonicCaptchaStatus='expired'; 'expired'")
                runBlocking { session.poll() }
                assertFalse(session.ready)
                assertTrue(session.error.orEmpty().contains("expired"))
            }
        } finally {
            server?.stop(0)
            EventQueue.invokeAndWait { session.close(); browser.disposeBrowser(); frame.dispose() }
        }
    }

    private companion object {
        const val FIXTURE = """<!doctype html><html><head><meta charset="utf-8">
            <title>Harmonic CAPTCHA fixture</title></head><body>
            <h1>CAPTCHA browser fixture</h1><p>Deterministic local browser verification.</p>
            <textarea id="g-recaptcha-response">fixture-"response"-λ</textarea>
            <a id="escape" href="https://example.invalid/">Blocked external navigation</a>
            <script>window.__harmonicCaptchaStatus='ready';</script></body></html>"""
    }
}
