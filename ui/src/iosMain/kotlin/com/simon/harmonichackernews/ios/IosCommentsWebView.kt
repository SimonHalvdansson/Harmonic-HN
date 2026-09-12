package com.simon.harmonichackernews.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.simon.harmonichackernews.presentation.WebContentPolicy
import com.simon.harmonichackernews.presentation.WebPreloadEnvironment
import com.simon.harmonichackernews.ui.comments.CommentsComposeController
import com.simon.harmonichackernews.ui.navigation.ActivityNavigationTransitionDurationMillis
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.accessibilityLabel
import platform.UIKit.systemBackgroundColor
import platform.WebKit.*
import platform.darwin.NSObject
import platform.Foundation.HTTPMethod
import kotlinx.cinterop.ObjCSignatureOverride
import platform.Foundation.NSError
import platform.Foundation.NSURLErrorCancelled
import com.simon.harmonichackernews.presentation.WebContentPagePolicy
import com.simon.harmonichackernews.presentation.WebContentPageText
import com.simon.harmonichackernews.presentation.WebContentPlatformUrls
import com.simon.harmonichackernews.presentation.WebContentTiming
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment

/**
 * Lightweight destination owner; the native browser is created only when loading is requested.
 * Use on UIKit's main thread and dispose with the destination. Swift integration tests use the
 * same owner in an application host, where WebKit can run its renderer.
 */
@OptIn(ExperimentalForeignApi::class)
class IosCommentsWebView(
    initialUrl: String,
    private val archiveDomains: () -> Collection<String> = { emptyList() },
    private val openExternal: (String) -> Unit = {},
) {
    private val initialUrl = WebContentPolicy.validatedHttpUrl(initialUrl)
    private var loadedUrl: String? = null
    private var inverted = false
    private var appearance = UIUserInterfaceStyle.UIUserInterfaceStyleLight
    private var disposed = false
    private var generation = 0
    private var recoveringProcess = false
    private val delegate = IosBrowserDelegate(this)
    var prepareLoad: (String) -> String = { it }
    var onPageFinished: (String?) -> Unit = {}
    var onLoadFailed: () -> Unit = {}
    var visible: Boolean = false

    var view: WKWebView? by mutableStateOf(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var failure: String? by mutableStateOf(null)
        private set
    var dialog: IosBrowserDialog? by mutableStateOf(null)
        private set

    private fun createView(): WKWebView = WKWebView(
        frame = CGRectZero.readValue(),
        configuration = WKWebViewConfiguration().apply {
            websiteDataStore = platform.WebKit.WKWebsiteDataStore.defaultDataStore()
            defaultWebpagePreferences.allowsContentJavaScript = true
            allowsInlineMediaPlayback = true
        },
    ).apply {
        navigationDelegate = delegate
        UIDelegate = delegate
        allowsBackForwardNavigationGestures = true
        allowsLinkPreview = true
        setOpaque(false)
        backgroundColor = UIColor.systemBackgroundColor
        scrollView.backgroundColor = UIColor.systemBackgroundColor
        accessibilityLabel = "Article web view"
        overrideUserInterfaceStyle = appearance
    }

    fun ensureLoaded() {
        if (loadedUrl == null) initialUrl?.let(::load)
    }

    suspend fun preloadAfterOpening(
        firstDraw: Deferred<Unit>,
        mode: String,
        minimumBatteryPercent: Int,
        environment: () -> WebPreloadEnvironment,
    ) {
        firstDraw.await()
        // Hidden browser startup must not compete with the opening animation.
        delay(ActivityNavigationTransitionDurationMillis.toLong())
        if (WebContentPolicy.shouldPreload(mode, minimumBatteryPercent, environment())) ensureLoaded()
    }

    fun load(url: String): Boolean {
        if (disposed) return false
        val plan = WebContentPolicy.resolveUrl(url, archiveDomains()) ?: return false
        val safeUrl = WebContentPolicy.validatedHttpUrl(prepareLoad(plan.loadUrl)) ?: return false
        return try {
            val nativeUrl = NSURL(string = safeUrl)
            val browser = view ?: createView().also { view = it }
            navigationStarted(safeUrl)
            browser.loadRequest(NSMutableURLRequest.requestWithURL(
                URL = nativeUrl,
                cachePolicy = platform.Foundation.NSURLRequestUseProtocolCachePolicy,
                timeoutInterval = WebContentTiming.LOAD_TIMEOUT_MILLIS / 1000.0,
            ))
            true
        } catch (_: Exception) {
            navigationFailed("Couldn't open this website")
            false
        }
    }

    fun reload() {
        recoveringProcess = false
        if (failure != null) loadedUrl?.let(::load)
        else if (loadedUrl == null) ensureLoaded()
        else {
            navigationStarted(currentUrl())
            view?.reload()
        }
    }

    fun currentUrl(): String? = WebContentPagePolicy.externalBrowserUrl(
        currentUrl = if (failure != null) loadedUrl else view?.URL?.absoluteString ?: loadedUrl,
        storyUrl = initialUrl,
        platformUrls = WebContentPlatformUrls("about:harmonic-pdf", "about:harmonic-error"),
    )

    fun canGoBack(): Boolean = view?.canGoBack == true

    fun goBack() {
        view?.takeIf { it.canGoBack }?.let {
            navigationStarted(it.backForwardList.backItem?.URL?.absoluteString)
            it.goBack()
        }
    }

    /** WKWebView returns a native string, unlike Android's JSON-encoded evaluation result. */
    suspend fun evaluate(script: String): String? {
        val browser = view ?: return null
        if (disposed) return null
        val expectedGeneration = generation
        return suspendCancellableCoroutine { continuation ->
            browser.evaluateJavaScript(script) { value, error ->
                if (continuation.isActive) continuation.resume(
                    (value as? String)?.takeIf {
                        error == null && !disposed && generation == expectedGeneration
                    },
                )
            }
        }
    }

    suspend fun readPageText(loadIfNeeded: Boolean): String? =
        withTimeoutOrNull(WebContentTiming.SUMMARY_LOAD_TIMEOUT_MILLIS) {
            if (loadIfNeeded) ensureLoaded()
            if (view == null || disposed) return@withTimeoutOrNull null
            val wasLoading = loading
            snapshotFlow { loading }.first { !it }
            if (failure != null) return@withTimeoutOrNull null
            if (wasLoading) delay(WebContentTiming.SUMMARY_PAGE_TEXT_SETTLE_MILLIS)
            if (loading || failure != null) return@withTimeoutOrNull null
            evaluate(WebContentPageText.READ_COMMAND)?.take(WebContentPageText.MAX_PAGE_TEXT_CHARS)
                ?.takeIf { it.isNotBlank() }
        }

    fun updateAppearance(dark: Boolean, matchTheme: Boolean) {
        appearance = if (matchTheme && dark) UIUserInterfaceStyle.UIUserInterfaceStyleDark
        else UIUserInterfaceStyle.UIUserInterfaceStyleLight
        view?.overrideUserInterfaceStyle = appearance
    }

    fun toggleInversion() {
        if (view == null) return
        inverted = !inverted
        applyInversion()
    }

    private fun applyInversion() {
        val filter = if (inverted) "invert(1) hue-rotate(180deg)" else "none"
        view?.evaluateJavaScript(
            "document.documentElement.style.filter='$filter';",
            completionHandler = null,
        )
    }

    internal fun navigationStarted(url: String?) {
        if (disposed) return
        generation++
        loading = true
        failure = null
        if (url != null) loadedUrl = url
        finishDialog(null)
    }

    internal fun navigationFinished(url: String?) {
        if (disposed) return
        loading = false
        failure = null
        recoveringProcess = false
        if (url != null) loadedUrl = url
        if (inverted) applyInversion()
        onPageFinished(url)
    }

    internal fun navigationFailed(message: String) {
        if (disposed) return
        generation++
        loading = false
        failure = message
        finishDialog(null)
        onLoadFailed()
    }

    internal fun processTerminated() {
        if (disposed) return
        if (recoveringProcess) {
            navigationFailed("This website stopped responding")
        } else {
            recoveringProcess = true
            loadedUrl?.let(::load)
        }
    }

    internal fun allowNavigation(action: WKNavigationAction): Boolean {
        if (disposed) return false
        val url = action.request.URL?.absoluteString ?: return false
        val mainFrame = action.targetFrame?.mainFrame != false
        val safe = WebContentPolicy.validatedHttpUrl(url)
        if (safe == null) {
            if (url == "about:blank" || url.startsWith("blob:")) return true
            // Only a user-initiated link may leave the app. Hidden preloads and iframe redirects
            // must not launch another application unexpectedly.
            if (visible && action.navigationType == WKNavigationTypeLinkActivated) {
                openExternal(url)
            }
            return false
        }
        if (mainFrame && action.request.HTTPMethod == "GET") {
            val redirected = WebContentPolicy.resolveUrl(safe, archiveDomains())?.loadUrl ?: safe
            val target = prepareLoad(redirected)
            if (target != safe) {
                load(target)
                return false
            }
        }
        if (action.targetFrame == null) {
            view?.loadRequest(action.request)
            return false
        }
        return true
    }

    internal fun openNewWindow(action: WKNavigationAction) {
        if (allowNavigation(action)) view?.loadRequest(action.request)
    }

    internal fun unsupportedResponse(url: String?) {
        if (url != null) loadedUrl = url
        navigationFailed("This file needs to be opened in your browser")
    }

    internal fun showDialog(value: IosBrowserDialog) {
        finishDialog(null)
        if (disposed) value.complete(null) else dialog = value
    }

    fun finishDialog(result: String?) {
        val pending = dialog ?: return
        dialog = null
        pending.complete(result)
    }

    fun dispose() {
        disposed = true
        generation++
        loading = false
        finishDialog(null)
        view?.let {
            it.stopLoading()
            it.navigationDelegate = null
            it.UIDelegate = null
        }
        view = null
        prepareLoad = { it }
        onPageFinished = {}
        onLoadFailed = {}
    }
}

data class IosBrowserDialog(
    val origin: String,
    val message: String,
    val prompt: Boolean = false,
    val defaultText: String = "",
    val cancellable: Boolean = false,
    val complete: (String?) -> Unit,
)

@OptIn(ExperimentalForeignApi::class)
private class IosBrowserDelegate(private val owner: IosCommentsWebView) :
    NSObject(), WKNavigationDelegateProtocol, WKUIDelegateProtocol {
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        decisionHandler(if (owner.allowNavigation(decidePolicyForNavigationAction))
            WKNavigationActionPolicy.WKNavigationActionPolicyAllow
        else WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
        owner.navigationStarted(webView.URL?.absoluteString)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        owner.navigationFinished(webView.URL?.absoluteString)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        if (withError.code != NSURLErrorCancelled) owner.navigationFailed(withError.localizedDescription)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        if (withError.code != NSURLErrorCancelled) owner.navigationFailed(withError.localizedDescription)
    }

    override fun webViewWebContentProcessDidTerminate(webView: WKWebView) = owner.processTerminated()

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationResponse: WKNavigationResponse,
        decisionHandler: (WKNavigationResponsePolicy) -> Unit,
    ) {
        val unsupported = decidePolicyForNavigationResponse.forMainFrame &&
            !decidePolicyForNavigationResponse.canShowMIMEType
        if (unsupported) owner.unsupportedResponse(decidePolicyForNavigationResponse.response.URL?.absoluteString)
        decisionHandler(if (unsupported) WKNavigationResponsePolicy.WKNavigationResponsePolicyCancel
        else WKNavigationResponsePolicy.WKNavigationResponsePolicyAllow)
    }

    override fun webView(
        webView: WKWebView,
        createWebViewWithConfiguration: WKWebViewConfiguration,
        forNavigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures,
    ): WKWebView? {
        owner.openNewWindow(forNavigationAction)
        return null
    }

    override fun webView(
        webView: WKWebView,
        runJavaScriptAlertPanelWithMessage: String,
        initiatedByFrame: WKFrameInfo,
        completionHandler: () -> Unit,
    ) = owner.showDialog(IosBrowserDialog(
        origin = initiatedByFrame.securityOrigin.host,
        message = runJavaScriptAlertPanelWithMessage,
        complete = { completionHandler() },
    ))

    override fun webView(
        webView: WKWebView,
        runJavaScriptConfirmPanelWithMessage: String,
        initiatedByFrame: WKFrameInfo,
        completionHandler: (Boolean) -> Unit,
    ) = owner.showDialog(IosBrowserDialog(
        origin = initiatedByFrame.securityOrigin.host,
        message = runJavaScriptConfirmPanelWithMessage,
        cancellable = true,
        complete = { completionHandler(it != null) },
    ))

    override fun webView(
        webView: WKWebView,
        runJavaScriptTextInputPanelWithPrompt: String,
        defaultText: String?,
        initiatedByFrame: WKFrameInfo,
        completionHandler: (String?) -> Unit,
    ) = owner.showDialog(IosBrowserDialog(
        origin = initiatedByFrame.securityOrigin.host,
        message = runJavaScriptTextInputPanelWithPrompt,
        prompt = true,
        defaultText = defaultText.orEmpty(),
        cancellable = true,
        complete = completionHandler,
    ))
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalComposeUiApi::class,
    ExperimentalForeignApi::class,
)
@Composable
internal fun IosCommentsScaffold(
    controller: CommentsComposeController,
    webView: IosCommentsWebView,
    reserveUpButtonInset: Boolean,
    comments: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val webViewTopInset = if (reserveUpButtonInset) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    } else {
        0.dp
    }
    val peekHeight = navigationBottom + if (controller.displaySettings?.isTablet == true) {
        81.dp
    } else {
        72.dp
    }
    val sheetState = rememberBottomSheetState(
        initialValue = if (controller.initialShowWebsite) {
            SheetValue.PartiallyExpanded
        } else {
            SheetValue.Expanded
        },
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    LaunchedEffect(webView, controller.initialShowWebsite) {
        if (controller.initialShowWebsite) webView.ensureLoaded()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullHeight = maxHeight
        val travelPx = with(density) { (fullHeight - peekHeight).toPx().coerceAtLeast(1f) }

        LaunchedEffect(controller.sheetRequest) {
            val request = controller.sheetRequest ?: return@LaunchedEffect
            if (request.expanded) sheetState.expand() else sheetState.partialExpand()
            controller.consumeSheetRequest(request)
        }
        LaunchedEffect(sheetState, travelPx) {
            snapshotFlow { runCatching { sheetState.requireOffset() }.getOrNull() }
                .collect { offset ->
                    // A direct drag can reveal the article without issuing a sheet request.
                    if (offset != null && offset > 0.5f) webView.ensureLoaded()
                    val expandedFraction = offset
                        ?.let { 1f - (it / travelPx) }
                        ?.coerceIn(0f, 1f)
                        ?: if (sheetState.currentValue == SheetValue.Expanded) 1f else 0f
                    controller.updateSheet(expandedFraction, controller.topInsetPx)
                    webView.visible = expandedFraction < 0.99f
                    controller.listener.onSheetProgressChanged(expandedFraction)
                }
        }
        LaunchedEffect(sheetState) {
            snapshotFlow { sheetState.currentValue }
                .distinctUntilChanged()
                .collect { value ->
                    controller.listener.onSheetSettled(value == SheetValue.Expanded)
                }
        }

        BottomSheetScaffold(
            modifier = Modifier.fillMaxSize(),
            scaffoldState = scaffoldState,
            sheetPeekHeight = peekHeight,
            sheetMaxWidth = androidx.compose.ui.unit.Dp.Unspecified,
            sheetShape = RectangleShape,
            sheetContainerColor = HarmonicTheme.colors.background,
            sheetContentColor = HarmonicTheme.colors.storyNormal,
            sheetShadowElevation = 16.dp,
            sheetDragHandle = null,
            sheetSwipeEnabled = true,
            containerColor = Color.Transparent,
            contentColor = HarmonicTheme.colors.storyNormal,
            sheetContent = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(fullHeight)
                        .background(HarmonicTheme.colors.background),
                ) {
                    comments()
                }
            },
        ) {
            Box(Modifier.fillMaxSize().padding(top = webViewTopInset)) {
                val browser = webView.view
                if (browser != null && webView.failure == null) UIKitView(
                    factory = { browser },
                    modifier = Modifier.fillMaxSize(),
                    properties = UIKitInteropProperties(
                        isInteractive = true,
                        isNativeAccessibilityEnabled = true,
                    ),
                )
                if (webView.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                webView.failure?.let { message ->
                    Column(
                        Modifier.fillMaxSize().background(HarmonicTheme.colors.background).padding(24.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(message)
                        TextButton(onClick = webView::reload) { Text("Retry") }
                        TextButton(onClick = {
                            controller.listener.onSheetAction(
                                com.simon.harmonichackernews.presentation.CommentsSheetAction.BROWSER,
                            )
                        }) { Text("Open in browser") }
                    }
                }
            }
        }
    }
    val dialog = webView.dialog
    if (dialog != null && controller.sheetSlideOffset < 0.5f) {
        var input by remember(dialog) { mutableStateOf(dialog.defaultText) }
        AlertDialog(
            onDismissRequest = { webView.finishDialog(null) },
            title = { Text(dialog.origin) },
            text = {
                Column {
                    Text(dialog.message)
                    if (dialog.prompt) OutlinedTextField(input, onValueChange = { input = it })
                }
            },
            confirmButton = {
                TextButton(onClick = { webView.finishDialog(input) }) { Text("OK") }
            },
            dismissButton = {
                if (dialog.cancellable) TextButton(onClick = { webView.finishDialog(null) }) { Text("Cancel") }
            },
        )
    }
}
