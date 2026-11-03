package com.simon.harmonichackernews.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.presentation.CaptchaResponseGate
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.ui.common.CaptchaDialogLayout
import com.simon.harmonichackernews.utils.HackerNewsCaptchaWebProtocol
import com.simon.harmonichackernews.utils.HackerNewsLinks
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import org.eclipse.swt.SWT
import org.jetbrains.compose.resources.stringResource
import java.awt.Canvas
import java.net.URI
import kotlin.coroutines.resume

@Composable
internal fun DesktopCaptchaDialog(
    challenge: HackerNewsCaptchaChallenge,
    onDismiss: () -> Unit,
    onCaptchaResponse: (String) -> Unit,
) {
    var attempt by remember(challenge) { mutableIntStateOf(0) }
    val session = remember(challenge, attempt) { DesktopCaptchaBrowserSession(challenge.captchaHtml) }
    val gate = remember(session) { CaptchaResponseGate() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val latestResponse by rememberUpdatedState(onCaptchaResponse)
    val latestDismiss by rememberUpdatedState(onDismiss)
    val incomplete = stringResource(Res.string.captcha_dialog_complete_error)
    val webDescription = stringResource(Res.string.captcha_dialog_web_content_description)
    var responseError by remember(session) { mutableStateOf<String?>(null) }
    var reading by remember(session) { mutableStateOf(false) }
    val canvas: Canvas? = remember(session) {
        val onState: (DesktopBrowserHost, DesktopBrowserSnapshot) -> Unit = session::update
        val onError: (Throwable) -> Unit = { session.fail(desktopCaptchaStartupError(it)) }
        val policy = HackerNewsCaptchaWebProtocol::allowsNavigation
        val escape = { gate.close(); latestDismiss() }
        when {
            System.getProperty("os.name").startsWith("Mac", true) ->
                MacWkWebViewCanvas(onState, onError, navigationPolicy = policy, onEscape = escape)
            System.getProperty("os.name").startsWith("Windows", true) ->
                SwtEdgeBrowserCanvas(onState, onError, navigationPolicy = policy, onEscape = escape)
            System.getProperty("os.name").startsWith("Linux", true) ->
                SwtEdgeBrowserCanvas(onState, onError, browserStyle = SWT.WEBKIT,
                    navigationPolicy = policy, onEscape = escape)
            else -> null
        }?.also { it.isVisible = false }
    }
    val browser = canvas as? DesktopBrowserHost
    DisposableEffect(session, browser) {
        if (browser == null) session.fail("This desktop operating system has no supported CAPTCHA browser.")
        else session.attach(browser, usesBaseUrl = browser is MacWkWebViewCanvas)
        onDispose {
            gate.close()
            session.close()
            browser?.disposeBrowser()
        }
    }
    LaunchedEffect(session) {
        focusManager.clearFocus(force = true)
        while (isActive) {
            session.poll()
            delay(350)
        }
    }
    CaptchaDialogLayout(
        loading = session.loading,
        error = session.error ?: responseError,
        onDismiss = { gate.close(); onDismiss() },
        onContinue = {
            val ticket = if (session.ready) gate.beginRead() else null
            if (ticket != null) {
                reading = true
                responseError = null
                scope.launch {
                    val response = session.readResponse()
                    reading = false
                    val accepted = gate.finishRead(ticket, response)
                    if (accepted != null) latestResponse(accepted)
                    else if (session.ready) responseError = incomplete
                }
            }
        },
        continueEnabled = session.ready && !reading,
        onRetry = { gate.close(); attempt++ },
        webContent = {
            if (canvas != null) {
                SwingPanel(
                    factory = { canvas },
                    update = {
                        it.accessibleContext?.accessibleName = webDescription
                        browser?.setBrowserVisible(session.ready)
                    },
                    modifier = Modifier.fillMaxSize().semantics { contentDescription = webDescription },
                    background = MaterialTheme.colorScheme.surface,
                )
            } else {
                Text("Embedded CAPTCHA browser unavailable.")
            }
        },
    )
}

/** A native browser document can fail/retry independently of the pending HN action or draft. */
internal class DesktopCaptchaBrowserSession(
    private val html: String,
    private val baseUrl: String = HackerNewsLinks.ROOT_URL,
    // This static HTML page currently has no CSP. The feed's response policy is unsuitable
    // for hosting an app-generated challenge and survives document.open()/document.write().
    private val bootstrapUrl: String = if (baseUrl == HackerNewsLinks.ROOT_URL)
        "${HackerNewsLinks.BASE_URL}/newsfaq.html" else baseUrl,
) {
    var loading by mutableStateOf(true)
        private set
    var ready by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var host: DesktopBrowserHost? = null
    private var pageLoaded = false
    private var installed = false
    private var closed = false
    private var generation = 0
    private var startedAt = System.nanoTime()
    private val origin = URI(baseUrl).let { uri ->
        "${uri.scheme}://${uri.rawAuthority}"
    }

    fun attach(browser: DesktopBrowserHost, usesBaseUrl: Boolean) {
        if (closed) return
        generation++
        host = browser
        ready = false
        loading = true
        error = null
        pageLoaded = false
        installed = usesBaseUrl
        startedAt = System.nanoTime()
        if (usesBaseUrl) (browser as MacWkWebViewCanvas).loadHtmlWithBaseUrl(html, baseUrl)
        else browser.loadUrl(bootstrapUrl)
    }

    fun update(browser: DesktopBrowserHost, snapshot: DesktopBrowserSnapshot) {
        if (closed || browser !== host) return
        snapshot.loadError?.let { fail("The CAPTCHA browser could not load. Check your connection and retry."); return }
        // The newly attached native view can publish its initial about:blank snapshot before
        // the queued challenge navigation starts. That document is never eligible for polling.
        if (!snapshot.isLoading && snapshot.url.isNotBlank() && snapshot.url != "about:blank") {
            pageLoaded = true
        }
    }

    suspend fun poll() {
        if (closed || error != null) return
        if (System.nanoTime() - startedAt > 45_000_000_000L && !ready) {
            fail("The CAPTCHA could not finish loading. Check your connection and retry.")
            return
        }
        if (!pageLoaded) return
        val ticket = generation
        if (!installed) {
            // SWT has no loadDataWithBaseURL equivalent. First load a real trusted HTTPS
            // document, then replace its generated contents while retaining that origin.
            val result = evaluate(desktopCaptchaInjectionScript(html, origin))
            if (closed || ticket != generation) return
            if (result == "unsupported-document") {
                fail("The CAPTCHA bootstrap did not return an HTML page. Retry to reconnect.")
                return
            }
            if (result != "installed") {
                fail("The CAPTCHA browser could not establish the Hacker News origin. Retry to reconnect.")
                return
            }
            installed = true
        }
        val status = evaluate(desktopCaptchaStatusScript(origin))
        if (closed || ticket != generation) return
        when (status) {
            "ready", "solved" -> { loading = false; ready = true }
            "loading" -> if (ready) {
                loading = true
                ready = false
                startedAt = System.nanoTime()
            }
            "error" -> fail("The CAPTCHA service could not load. Check your connection and retry.")
            "policy-error" -> fail("The CAPTCHA page blocked a required script or frame. Retry to reconnect.")
            "expired" -> fail("The CAPTCHA expired. Retry to load a new challenge.")
            "untrusted" -> fail("The CAPTCHA browser left the Hacker News document. Retry to reconnect.")
            null -> fail("The CAPTCHA browser did not respond. Retry to create a new browser.")
        }
    }

    suspend fun readResponse(): String? {
        if (!ready || closed) return null
        val ticket = generation
        // Native WKWebView and SWT return raw JS strings; stringify to use the Android/shared decoder.
        val value = evaluate("(function(){if(location.origin!==${JsonPrimitive(origin)})return '';" +
            "if(window.__harmonicCaptchaPolicyFailed)return '';" +
            "var status=(${HackerNewsCaptchaWebProtocol.STATUS_SCRIPT});" +
            "if(status==='expired'||status==='error')return '';" +
            "return JSON.stringify(${HackerNewsCaptchaWebProtocol.RESPONSE_SCRIPT});})()")
        if (closed || ticket != generation) return null
        if (value == null) {
            fail("The CAPTCHA browser did not respond. Retry without resubmitting your action.")
            return null
        }
        return HackerNewsCaptchaWebProtocol.decodeResponse(value)
    }

    fun fail(message: String) {
        if (closed) return
        generation++
        loading = false
        ready = false
        error = message
    }

    fun close() {
        closed = true
        generation++
        host = null
    }

    private suspend fun evaluate(script: String): String? {
        val browser = host ?: return null
        return withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine { continuation ->
                browser.evaluateJavaScriptResult(script) { value ->
                    if (continuation.isActive) continuation.resume(value)
                }
            }
        }
    }
}

internal fun desktopCaptchaInjectionScript(html: String, origin: String): String =
    "(function(){if(location.origin!==${JsonPrimitive(origin)})return 'untrusted';" +
        "if(document.contentType!=='text/html')return 'unsupported-document';" +
        "document.open();window.__harmonicCaptchaPolicyFailed=false;" +
        // document.open removes prior listeners. Install from the native evaluation before the
        // generated script runs, so a future server CSP change becomes an actionable load error.
        "document.addEventListener('securitypolicyviolation',function(e){" +
        "if(e.disposition==='report')return;" +
        "if(e.target!==document&&(!e.target||e.target.ownerDocument!==document))return;" +
        "var d=e.effectiveDirective||'';" +
        "if(d!=='script-src'&&d!=='script-src-elem'&&d!=='frame-src'&&d!=='child-src')return;" +
        "var blocked=e.blockedURI||'';var provider=false;" +
        "if(blocked==='inline'&&d.indexOf('script-src')===0)provider=true;" +
        "else try{var u=new URL(blocked,location.href);" +
        "provider=u.protocol==='https:'&&" +
        "['www.google.com','google.com','recaptcha.google.com','www.gstatic.com','recaptcha.net','www.recaptcha.net'].indexOf(u.hostname)>=0&&" +
        // Cross-origin CSP events may redact the resource path to its origin.
        "(u.pathname==='/'||u.pathname.indexOf('/recaptcha/')===0);}catch(ignore){}" +
        "if(provider){window.__harmonicCaptchaPolicyFailed=true;window.__harmonicCaptchaStatus='error';}});" +
        "document.write(${JsonPrimitive(html)});document.close();return 'installed';})()"

internal fun desktopCaptchaStatusScript(origin: String): String =
    "(function(){return location.origin===${JsonPrimitive(origin)}?" +
        "(window.__harmonicCaptchaPolicyFailed?'policy-error':(${HackerNewsCaptchaWebProtocol.STATUS_SCRIPT})):'untrusted';})()"

internal fun desktopCaptchaStartupError(error: Throwable, osName: String = System.getProperty("os.name")): String =
    when {
        osName.startsWith("Windows", true) ->
            "The CAPTCHA browser requires Microsoft Edge WebView2 Runtime. Install or repair it, then retry."
        osName.startsWith("Linux", true) ->
            "The CAPTCHA browser requires GTK 3, WebKitGTK 4.1, and X11/XWayland. " +
                (error.message?.takeIf { it.contains("Wayland") } ?: "Install the missing native libraries, then retry.")
        else -> "The native CAPTCHA browser could not start. Retry to create a new browser."
    }
