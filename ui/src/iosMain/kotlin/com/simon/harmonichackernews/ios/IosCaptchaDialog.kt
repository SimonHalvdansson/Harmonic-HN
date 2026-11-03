package com.simon.harmonichackernews.ios

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.presentation.CaptchaResponseGate
import com.simon.harmonichackernews.ui.common.CaptchaDialogLayout
import com.simon.harmonichackernews.utils.HackerNewsCaptchaWebProtocol
import com.simon.harmonichackernews.utils.HackerNewsLinks
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIColor
import platform.UIKit.accessibilityLabel
import platform.UIKit.endEditing
import platform.UIKit.systemBackgroundColor
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKNavigationResponse
import platform.WebKit.WKNavigationResponsePolicy
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject
import kotlin.coroutines.resume

/** A scene-local WebKit challenge. Only the token is returned; shared networking submits it. */
@OptIn(ExperimentalForeignApi::class)
class IosCaptchaBrowser(private val html: String) {
    private val gate = CaptchaResponseGate()
    private var disposed = false
    private var completed = false
    private var documentLoaded = false
    private var delegate: IosCaptchaDelegate? = null

    var view: WKWebView? by mutableStateOf(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    var extracting by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var canRetry by mutableStateOf(false)
        private set
    var generation by mutableStateOf(0)
        private set
    var rejectedNavigationCount = 0
        private set

    fun load() {
        if (disposed || completed) return
        generation++
        gate.reset()
        releaseView()
        loading = true
        documentLoaded = false
        ready = false
        extracting = false
        error = null
        canRetry = false
        try {
            val browser = WKWebView(
                frame = CGRectZero.readValue(),
                configuration = WKWebViewConfiguration().apply {
                    websiteDataStore = WKWebsiteDataStore.defaultDataStore()
                    defaultWebpagePreferences.allowsContentJavaScript = true
                },
            )
            delegate = IosCaptchaDelegate(this, browser).also {
                browser.navigationDelegate = it
                browser.UIDelegate = it
            }
            browser.allowsBackForwardNavigationGestures = false
            browser.allowsLinkPreview = false
            browser.backgroundColor = UIColor.systemBackgroundColor
            browser.scrollView.scrollsToTop = false
            browser.accessibilityLabel = "Hacker News CAPTCHA"
            view = browser
            // Treat generated HTML as the HTTPS response without making a request to HN.
            // This gives reCAPTCHA the real HN origin rather than an opaque local document.
            browser.loadSimulatedRequest(
                NSURLRequest.requestWithURL(NSURL(string = HackerNewsLinks.ROOT_URL)),
                responseHTMLString = html,
            )
        } catch (_: Exception) {
            fail("The CAPTCHA browser could not start. Try again.")
        }
    }

    internal fun finished(browser: WKWebView) {
        if (disposed || completed || view !== browser || canRetry) return
        documentLoaded = true
    }

    /** Poll the shared widget status, including failures that don't fail the main document. */
    suspend fun refreshStatus() {
        if (disposed || completed || canRetry || !documentLoaded) return
        val browser = view ?: return
        val expectedGeneration = generation
        val status = evaluate(browser, HackerNewsCaptchaWebProtocol.TRUSTED_STATUS_SCRIPT, expectedGeneration)
        if (disposed || completed || generation != expectedGeneration || view !== browser) return
        when (status) {
            "ready", "solved" -> {
                loading = false
                ready = true
                if (status == "solved") error = null
            }
            "expired" -> {
                loading = false
                ready = true
                error = "The CAPTCHA expired. Complete it again before continuing."
            }
            "error" -> fail("The CAPTCHA could not load. Check your connection and try again.")
            "untrusted" -> fail("The CAPTCHA browser lost its secure page. Try again.")
        }
    }

    internal fun failed(browser: WKWebView, message: String) {
        if (view === browser) fail(message)
    }

    internal fun rejectedNavigation() {
        rejectedNavigationCount++
    }

    fun loadTimedOut(expectedGeneration: Int) {
        if (generation == expectedGeneration && loading) {
            fail("The CAPTCHA could not load. Check your connection and try again.")
        }
    }

    private fun fail(message: String) {
        if (disposed || completed) return
        generation++
        gate.reset()
        loading = false
        documentLoaded = false
        ready = false
        extracting = false
        canRetry = true
        error = message
        view?.stopLoading()
    }

    suspend fun readResponse(): String? {
        if (disposed || !ready || completed) return null
        val browser = view ?: return null
        val ticket = gate.beginRead() ?: return null
        val expectedGeneration = generation
        extracting = true
        error = null
        val value = try {
            evaluate(browser, HackerNewsCaptchaWebProtocol.TRUSTED_RESPONSE_SCRIPT, expectedGeneration)
        } catch (cancelled: CancellationException) {
            gate.finishRead(ticket, null)
            if (generation == expectedGeneration) extracting = false
            throw cancelled
        }
        if (disposed || generation != expectedGeneration || view !== browser) return null
        extracting = false
        val response = gate.finishRead(ticket, value)
        if (response == null) {
            error = if (value == null) "The CAPTCHA response could not be read. Try again."
            else "Complete the CAPTCHA before continuing."
        } else {
            completed = true
            ready = false
            endEditing()
        }
        return response
    }

    private suspend fun evaluate(browser: WKWebView, script: String, expectedGeneration: Int): String? =
        withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { continuation ->
                browser.evaluateJavaScript(script) { value, failure ->
                    if (continuation.isActive) continuation.resume(
                        (value as? String)?.takeIf {
                            failure == null && !disposed && generation == expectedGeneration && view === browser
                        },
                    )
                }
            }
        }

    fun endEditing() {
        view?.endEditing(true)
    }

    private fun releaseView() {
        view?.let {
            it.endEditing(true)
            it.stopLoading()
            it.navigationDelegate = null
            it.UIDelegate = null
        }
        view = null
        delegate = null
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        generation++
        gate.close()
        releaseView()
        loading = false
        ready = false
        extracting = false
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosCaptchaDelegate(
    private val owner: IosCaptchaBrowser,
    private val browser: WKWebView,
) : NSObject(), WKNavigationDelegateProtocol, WKUIDelegateProtocol {
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val frame = decidePolicyForNavigationAction.targetFrame
        val allowed = frame != null && HackerNewsCaptchaWebProtocol.allowsNavigation(
            decidePolicyForNavigationAction.request.URL?.absoluteString,
            isMainFrame = frame.mainFrame,
        )
        if (!allowed) owner.rejectedNavigation()
        decisionHandler(if (allowed) WKNavigationActionPolicy.WKNavigationActionPolicyAllow
        else WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) = owner.finished(browser)

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        if (withError.code != NSURLErrorCancelled) {
            owner.failed(browser, "The CAPTCHA could not load. Check your connection and try again.")
        }
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        if (withError.code != NSURLErrorCancelled) {
            owner.failed(browser, "The CAPTCHA could not load. Check your connection and try again.")
        }
    }

    override fun webViewWebContentProcessDidTerminate(webView: WKWebView) =
        owner.failed(browser, "The CAPTCHA browser stopped responding. Try again.")

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationResponse: WKNavigationResponse,
        decisionHandler: (WKNavigationResponsePolicy) -> Unit,
    ) {
        val allowed = decidePolicyForNavigationResponse.canShowMIMEType
        if (!allowed && decidePolicyForNavigationResponse.forMainFrame) {
            owner.failed(browser, "The CAPTCHA browser could not display this challenge. Try again.")
        }
        decisionHandler(if (allowed) WKNavigationResponsePolicy.WKNavigationResponsePolicyAllow
        else WKNavigationResponsePolicy.WKNavigationResponsePolicyCancel)
    }

    override fun webView(
        webView: WKWebView,
        createWebViewWithConfiguration: WKWebViewConfiguration,
        forNavigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures,
    ): WKWebView? = null
}

@OptIn(ExperimentalForeignApi::class)
@Composable
fun IosCaptchaDialog(
    challenge: HackerNewsCaptchaChallenge,
    onDismiss: () -> Unit,
    onCaptchaResponse: (String) -> Unit,
) {
    val browser = remember(challenge) { IosCaptchaBrowser(challenge.captchaHtml) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dismiss by rememberUpdatedState(onDismiss)
    val respond by rememberUpdatedState(onCaptchaResponse)
    LaunchedEffect(browser) {
        focus.clearFocus(force = true)
        keyboard?.hide()
        browser.load()
    }
    LaunchedEffect(browser, browser.generation, browser.loading) {
        val generation = browser.generation
        if (browser.loading) {
            delay(30_000)
            browser.loadTimedOut(generation)
        }
    }
    LaunchedEffect(browser, browser.generation) {
        while (!browser.canRetry) {
            browser.refreshStatus()
            delay(400)
        }
    }
    DisposableEffect(browser) { onDispose(browser::dispose) }
    CaptchaDialogLayout(
        loading = browser.loading || browser.extracting,
        error = browser.error,
        onDismiss = {
            browser.endEditing()
            browser.dispose()
            dismiss()
        },
        onContinue = { scope.launch { browser.readResponse()?.let(respond) } },
        continueEnabled = browser.ready && !browser.extracting,
        onRetry = if (browser.canRetry) browser::load else null,
        webContent = {
            browser.view?.takeUnless { browser.canRetry }?.let { view ->
                key(view) {
                    UIKitView(
                        factory = { view },
                        modifier = Modifier.fillMaxSize(),
                        properties = UIKitInteropProperties(
                            isInteractive = true,
                            isNativeAccessibilityEnabled = true,
                        ),
                    )
                }
            }
        },
    )
}
