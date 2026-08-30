package com.simon.harmonichackernews

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.CustomViewCallback
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.linkpreview.LinkPreviewController
import com.simon.harmonichackernews.platform.AndroidExternalLinkLauncher
import com.simon.harmonichackernews.presentation.WebContentFailure
import com.simon.harmonichackernews.presentation.WebContentAssets
import com.simon.harmonichackernews.presentation.WebContentPageKind
import com.simon.harmonichackernews.presentation.WebContentPagePolicy
import com.simon.harmonichackernews.presentation.WebContentPlatformUrls
import com.simon.harmonichackernews.presentation.WebContentPolicy
import com.simon.harmonichackernews.presentation.WebPreloadEnvironment
import com.simon.harmonichackernews.presentation.ReaderModePageDecision
import com.simon.harmonichackernews.presentation.ReaderModeStateChange
import com.simon.harmonichackernews.presentation.ReaderModeToggleDecision
import com.simon.harmonichackernews.presentation.WebContentRuntime
import com.simon.harmonichackernews.presentation.WebContentCopy
import com.simon.harmonichackernews.presentation.WebContentPageText
import com.simon.harmonichackernews.presentation.EmbeddedWebContentPage
import com.simon.harmonichackernews.presentation.EmbeddedWebContentSession
import com.simon.harmonichackernews.presentation.WebContentDriver
import com.simon.harmonichackernews.presentation.WebContentDriverState
import com.simon.harmonichackernews.presentation.WebContentTiming
import com.simon.harmonichackernews.network.PdfDownloadService
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.settings.AndroidSettingsResources
import com.simon.harmonichackernews.settings.ReadingPreferences
import com.simon.harmonichackernews.settings.WebViewPreferences
import com.simon.harmonichackernews.utils.ThemeUtils
import com.simon.harmonichackernews.utils.AndroidDisplay
import com.simon.harmonichackernews.utils.AndroidNetworkStatus
import com.simon.harmonichackernews.cache.StoryCacheService
import com.simon.harmonichackernews.presentation.UserMessageDuration
import com.simon.harmonichackernews.utils.HarmonicLog
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class CommentsWebViewController(
    private val coordinator: CommentsCoordinator,
    private val story: Story?,
    private val linkPreviewController: LinkPreviewController,
    webContentRuntime: WebContentRuntime,
    private val storyCache: StoryCacheService,
    private val pdfDownloads: PdfDownloadService,
    private val coroutineScope: CoroutineScope,
    private val callbacks: Callbacks
) {

    internal interface Callbacks {
        fun startActivity(intent: Intent)

        fun openExternalLink(url: String)

        fun syncOnBackPressedCallbackEnabledState()

        fun onReaderModeChanged(enabled: Boolean)

        fun onReaderModeAvailabilityChanged(available: Boolean)

        fun onFullscreenChanged(fullscreen: Boolean)
    }

    internal fun interface PageTextCallback {
        fun onResult(text: String?)
    }

    private val webViewHandler = Handler(Looper.getMainLooper())
    private val webViewBackdropFadeInRunnable = Runnable {
        webViewBackdrop?.animate()
            ?.alpha(1f)
            ?.setDuration(300)
            ?.start()
    }

    private var webView: WebView? = null
    private var webViewContainer: FrameLayout? = null
    private var fullscreenContainer: FrameLayout? = null
    private var webViewBackdrop: View? = null
    private var downloadButton: MaterialButton? = null
    private var progressIndicator: LinearProgressIndicator? = null
    private var progressAnimator: ValueAnimator? = null
    private var showWebsite = false
    private var integratedWebview = true
    private var preloadWebview: String? = "never"
    private var preloadWebviewMinimumBattery = WebViewPreferences.DEFAULT_MINIMUM_BATTERY
    private var matchWebviewTheme = true
    private lateinit var readingPreferences: ReadingPreferences
    private val webContentDriver = AndroidWebContentDriver()
    private val webContentSession = EmbeddedWebContentSession(webContentRuntime, webContentDriver)
    private val webContentController = webContentSession.controller
    private val readerModeResources = AndroidReaderModeResources()
    private val pdfWebViewSession = AndroidPdfWebViewSession()
    private val readerModeFeatureEnabled: Boolean get() = webContentSession.readerState.featureEnabled
    var isBlockingAds: Boolean = true
        private set
    private var startedLoading = false
    private var initializedWebView = false
    private val showingErrorPage: Boolean
        get() = webContentSession.state.page == EmbeddedWebContentPage.ERROR
    private val showingCachedArticlePage: Boolean
        get() = webContentSession.state.page == EmbeddedWebContentPage.CACHED_CONTENT
    private var clearWebViewHistoryOnNextFinish = false
    private val webContentLoad = webContentRuntime.load
    private val adBlocklist = webContentRuntime.adBlocklist
    private var pendingSummaryCallback: PageTextCallback? = null
    private var lastPageFinishedGeneration = -1
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null
    val isReaderModeAvailable: Boolean get() = webContentSession.readerState.available
    private val readerModeEnabled: Boolean get() = webContentSession.readerState.enabled
    private var touchGestureStartScrollX = 0
    private var touchGestureStartScrollY = 0
    private var touchGestureStartScrollCaptured = false
    private var predictiveBackScrollX = 0
    private var predictiveBackScrollY = 0
    private var predictiveBackScrollFrozen = false

    fun bindViews(
        host: CommentsWebViewHost,
        progressIndicator: LinearProgressIndicator
    ) {
        this.progressIndicator = progressIndicator
        progressIndicator.visibility = View.GONE
        progressIndicator.progress = 0
        webView = null
        downloadButton = host.downloadButton
        webViewContainer = host.webViewContainer
        fullscreenContainer = host.fullscreenContainer
        webViewBackdrop = host.webViewBackdrop
    }

    fun configure(
        showWebsite: Boolean,
        integratedWebview: Boolean,
        readingPreferences: ReadingPreferences,
        blockAds: Boolean
    ) {
        this.showWebsite = showWebsite
        this.integratedWebview = integratedWebview
        this.readingPreferences = readingPreferences
        this.preloadWebview = readingPreferences.preloadWebViewMode
        this.preloadWebviewMinimumBattery = readingPreferences.preloadWebViewMinimumBattery
        this.matchWebviewTheme = readingPreferences.matchWebViewTheme
        applyReaderModeChange(
            webContentSession.configureReader(
                readingPreferences.readerModeEnabled,
                integratedWebview,
                readingPreferences.readerModeDefault,
            ),
        )
        this.isBlockingAds = blockAds
    }

    fun setIntegratedWebview(integratedWebview: Boolean) {
        this.integratedWebview = integratedWebview
        applyReaderModeChange(webContentSession.setReaderIntegrated(integratedWebview))
    }

    fun initializeForVisibleWebsite() {
        initialize()
        if (webView != null && !startedLoading) {
            startedLoading = true
            loadUrl(story?.url)
        }
    }

    /** Starts configured background loading only after the destination has drawn once. */
    fun initializeAfterFirstDraw() {
        val context = coordinator.context ?: return
        val shouldStartLoading = showWebsite || shouldPreloadStoryUrl(context) ||
            linkPreviewController.shouldInitializeWebViewForPreview(context)
        if (!shouldStartLoading) return
        initialize()
        if (webView != null && !startedLoading) {
            startedLoading = true
            loadUrl(story?.url)
        }
    }

    fun hasWebView(): Boolean {
        return webView != null
    }

    fun getLoadedPageText(callback: PageTextCallback) {
        webContentController.readPageText(callback::onResult)
    }

    fun canGoBack(): Boolean = webContentDriver.currentState().canGoBack

    val isShowingCustomView: Boolean
        get() = customView != null

    fun beginPredictiveBackScrollFreeze() {
        val currentWebView = webView ?: return
        if (predictiveBackScrollFrozen) {
            restorePredictiveBackScroll()
            return
        }
        predictiveBackScrollX = if (touchGestureStartScrollCaptured)
            touchGestureStartScrollX
        else
            currentWebView.scrollX
        predictiveBackScrollY = if (touchGestureStartScrollCaptured)
            touchGestureStartScrollY
        else
            currentWebView.scrollY
        predictiveBackScrollFrozen = true
        // The back gesture begins with the same edge drag that WebView would otherwise continue
        // interpreting as page input. Cancel its active gesture, then consume the remaining
        // stream in the touch listener until predictive back settles.
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        currentWebView.onTouchEvent(cancel)
        cancel.recycle()
        touchGestureStartScrollCaptured = false
        restorePredictiveBackScroll()
    }

    fun maintainPredictiveBackScrollFreeze() {
        if (predictiveBackScrollFrozen) {
            restorePredictiveBackScroll()
        }
    }

    fun endPredictiveBackScrollFreeze() {
        if (!predictiveBackScrollFrozen) {
            return
        }
        restorePredictiveBackScroll()
        predictiveBackScrollFrozen = false
        touchGestureStartScrollCaptured = false
    }

    private fun restorePredictiveBackScroll() {
        val currentWebView = webView ?: return
        if (currentWebView.scrollX != predictiveBackScrollX ||
            currentWebView.scrollY != predictiveBackScrollY
        ) {
            currentWebView.scrollTo(predictiveBackScrollX, predictiveBackScrollY)
        }
    }

    val isShowingOfflineOrCachedPage: Boolean
        get() = showingErrorPage || showingCachedArticlePage

    fun hasLastFailedUrl(): Boolean {
        return !TextUtils.isEmpty(webContentSession.state.lastFailedUrl)
    }

    fun isReaderModeEnabled(): Boolean {
        return readerModeEnabled
    }

    fun setContainerVisibility(visibility: Int) {
        webViewContainer?.visibility = visibility
    }

    fun setContainerPadding(left: Int, top: Int, right: Int, bottom: Int) {
        webViewContainer?.setPadding(left, top, right, bottom)
    }

    fun setContainerLayoutParams(params: ViewGroup.LayoutParams?) {
        webViewContainer?.layoutParams = params
    }

    fun setContainerBackgroundColor(color: Int) {
        webViewContainer?.setBackgroundColor(color)
    }

    fun goBackFromVisibleWebView() {
        val currentWebView = webView?.takeIf { it.canGoBack() } ?: return
        val currentDownloadButton = downloadButton
        if (currentDownloadButton?.isVisible == true && currentWebView.isGone) {
            currentWebView.isGone = false
            currentDownloadButton.isGone = true
        } else if (showingErrorPage) {
            webContentSession.showContent()
            if (currentWebView.canGoBackOrForward(-2)) {
                currentWebView.goBackOrForward(-2)
            } else {
                currentWebView.goBack()
            }
        } else {
            currentWebView.goBack()
        }
    }

    fun retryLastFailedUrl() {
        val failedUrl = webContentSession.beginRetry() ?: return
        webContentController.load(failedUrl, readingPreferences.archiveRedirectDomains)
    }

    fun reload() {
        webContentController.reload()
    }

    fun openCurrentOrStoryUrlInBrowser() {
        val url = WebContentPagePolicy.externalBrowserUrl(
            currentUrl = webView?.url,
            storyUrl = story?.url,
            platformUrls = WEB_CONTENT_URLS,
        )
        if (url == null) coordinator.showMessage(WebContentCopy.OPEN_URL_FAILED)
        else callbacks.openExternalLink(url)
    }

    fun disableAdBlockAndReload() {
        isBlockingAds = false
        val currentWebView = webView ?: return
        currentWebView.reload()

        coordinator.showMessage(WebContentCopy.AD_BLOCK_DISABLED)
    }

    fun toggleReaderMode() {
        if (!readerModeFeatureEnabled) {
            return
        }
        if (webView == null) {
            initialize()
        }
        val context = coordinator.context ?: return
        val currentWebView = webView ?: return
        if (coordinator.view == null) return

        val currentUrl = currentWebView.url
        if (showingErrorPage ||
            !WebContentPagePolicy.isReaderEligible(currentUrl, WEB_CONTENT_URLS)
        ) {
            coordinator.showMessage(WebContentCopy.READER_UNAVAILABLE_FOR_PAGE)
            return
        }

        val pageReady = startedLoading && !currentUrl.isNullOrEmpty() &&
            !(currentWebView.progress < 100 && webContentLoad.state.inProgress)
        val toggle = webContentSession.toggleReaderMode(
            pageEligible = true,
            pageReady = pageReady,
            storyUrlAvailable = !story?.url.isNullOrEmpty(),
        )
        applyReaderModeChange(toggle.change)
        when (val decision = toggle.decision) {
            ReaderModeToggleDecision.Unavailable -> {
                coordinator.showMessage(WebContentCopy.READER_UNAVAILABLE_FOR_PAGE)
                return
            }
            ReaderModeToggleDecision.LoadThenEnable -> {
                if (!startedLoading) {
                    startedLoading = true
                    loadUrl(story?.url)
                }
                coordinator.showMessage(WebContentCopy.READER_PENDING)
                return
            }
            is ReaderModeToggleDecision.Apply -> applyReaderMode(decision.enabled)
        }
    }

    fun disableReaderMode() {
        val result = webContentSession.disableReaderMode() ?: return
        applyReaderModeChange(result.change)
        val decision = result.decision as? ReaderModeToggleDecision.Apply ?: return
        applyReaderMode(decision.enabled)
    }

    private fun applyReaderMode(enable: Boolean, showFeedback: Boolean = true) {
        if (!readerModeFeatureEnabled) return
        val context = coordinator.context ?: return
        val targetWebView = webView ?: return
        if (coordinator.view == null) return

        val script = readerModeResources.script(context)
        if (TextUtils.isEmpty(script)) {
            applyReaderModeChange(webContentSession.setReaderUnavailableNow())
            if (showFeedback) {
                coordinator.showMessage(WebContentCopy.READER_UNAVAILABLE)
            }
            return
        }

        val generation = webContentLoad.state.generation
        webContentController.evaluateReaderMode(
            script = script.orEmpty(),
            theme = readerModeResources.theme(context, readingPreferences),
            enabled = enable,
        ) { status ->
            val callbackContext = coordinator.context
            if (callbackContext == null || targetWebView !== webView || generation != webContentLoad.state.generation || coordinator.view == null) {
                return@evaluateReaderMode
            }

            val result = webContentSession.applyReaderModeEvaluation(
                status = status,
                generation = generation,
                nowMillis = SystemClock.uptimeMillis(),
                showFeedback = showFeedback,
            )
            applyReaderModeChange(result.change)
            result.delayedUnavailableMillis?.let {
                scheduleDelayedReaderModeUnavailable(generation, it)
            }
            result.message?.let(coordinator::showMessage)
        }
    }

    private fun checkReaderModeAvailability(view: WebView, generation: Int) {
        val context = coordinator.context
        if (!canCheckReaderModeAvailability(view, generation, context)) {
            applyReaderModeChange(webContentSession.setReaderUnavailableNow())
            return
        }

        val script = readerModeResources.script(checkNotNull(context))
        if (TextUtils.isEmpty(script)) {
            applyReaderModeChange(webContentSession.setReaderUnavailableNow())
            return
        }

        webContentController.evaluateReaderModeAvailability(script.orEmpty()) { available ->
            val callbackContext = coordinator.context
            if (!canCheckReaderModeAvailability(view, generation, callbackContext)) {
                return@evaluateReaderModeAvailability
            }
            val result = webContentSession.applyReaderAvailability(
                available = available,
                generation = generation,
                nowMillis = SystemClock.uptimeMillis(),
            )
            applyReaderModeChange(result.change)
            result.delayedUnavailableMillis?.let {
                scheduleDelayedReaderModeUnavailable(generation, it)
            }
            if (result.scheduleRecheck) {
                scheduleReaderModeAvailabilityRecheck(view, generation)
            }
        }
    }

    private fun canCheckReaderModeAvailability(
        view: WebView,
        generation: Int,
        context: Context?
    ): Boolean {
        if (!readerModeFeatureEnabled || !integratedWebview || view !== webView || generation != webContentLoad.state.generation || context == null || coordinator.view == null) {
            return false
        }

        val currentUrl = view.getUrl()
        return !showingErrorPage &&
            WebContentPagePolicy.isReaderEligible(currentUrl, WEB_CONTENT_URLS)
    }

    private fun scheduleDelayedReaderModeUnavailable(generation: Int, delayMillis: Long) {
        webViewHandler.postDelayed(Runnable {
            webContentSession.applyDelayedReaderUnavailable(generation)
                ?.let(::applyReaderModeChange)
        }, delayMillis)
    }

    private fun scheduleReaderModeAvailabilityRecheck(view: WebView, generation: Int) {
        if (view !== webView) return

        webViewHandler.postDelayed(Runnable {
            if (view === webView &&
                webContentSession.shouldRunReaderAvailabilityRecheck(generation)
            ) {
                checkReaderModeAvailability(view, generation)
            }
        }, WebContentTiming.READER_AVAILABILITY_RECHECK_DELAY_MILLIS)
    }

    private fun applyReaderModeChange(change: ReaderModeStateChange) {
        if (change.availabilityChanged) {
            callbacks.onReaderModeAvailabilityChanged(change.current.available)
        }
        if (change.enabledChanged) {
            callbacks.onReaderModeChanged(change.current.enabled)
            callbacks.syncOnBackPressedCallbackEnabledState()
        }
    }

    fun toggleDarkMode() {
        val currentWebView = webView ?: return
        val settings = currentWebView.settings
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(
                settings,
                !WebSettingsCompat.isAlgorithmicDarkeningAllowed(settings)
            )
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            if (WebSettingsCompat.getForceDark(settings) == WebSettingsCompat.FORCE_DARK_ON) {
                WebSettingsCompat.setForceDark(
                    settings,
                    WebSettingsCompat.FORCE_DARK_OFF
                )
            } else {
                WebSettingsCompat.setForceDark(
                    settings,
                    WebSettingsCompat.FORCE_DARK_ON
                )
            }
        }
    }

    @SuppressLint("RequiresFeature", "SetJavaScriptEnabled")
    @Suppress("deprecation")
    fun initialize() {
        if (initializedWebView) {
            return
        }

        val context = coordinator.context
        if (context == null || coordinator.view == null) {
            return
        }

        val currentWebView = orInflateWebView ?: return
        webView = currentWebView
        initializedWebView = true

        currentWebView.webViewClient = MyWebViewClient()

        currentWebView.settings.apply {
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptEnabled = true
            domStorageEnabled = true
            setGeolocationEnabled(true)
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(currentWebView, true)

        currentWebView.setDownloadListener(object : DownloadListener {
            override fun onDownloadStart(
                url: String, userAgent: String?,
                contentDisposition: String?, mimetype: String?,
                contentLength: Long
            ) {
                if (!TextUtils.isEmpty(mimetype) && mimetype == PDF_MIME_TYPE && (url.startsWith("http://") || url.startsWith(
                        "https://"
                    ))
                ) {
                    downloadPdf(url, contentDisposition, mimetype, currentWebView.context)
                } else {
                    showDownloadButton(url, contentDisposition, mimetype)
                }
            }
        })

        currentWebView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (coordinator.context == null || coordinator.view == null || fullscreenContainer == null || webViewContainer == null) {
                    callback.onCustomViewHidden()
                    return
                }
                showCustomView(view, callback)
            }

            override fun onHideCustomView() {
                hideCustomView(false)
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                updateWebViewProgress(view, newProgress)
            }
        }

        if (matchWebviewTheme && ThemeUtils.isDarkMode(context)) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(currentWebView.settings, true)
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(
                    currentWebView.settings,
                    WebSettingsCompat.FORCE_DARK_ON
                )
            }
        }

        currentWebView.setBackgroundColor(Color.TRANSPARENT)

        if (shouldPreloadStoryUrl(context) || showWebsite || linkPreviewController.shouldInitializeWebViewForPreview(
                context
            )
        ) {
            loadUrl(story?.url)
            startedLoading = true
        }
    }

    private fun shouldPreloadStoryUrl(context: Context): Boolean {
        return WebContentPolicy.shouldPreload(
            preloadWebview,
            preloadWebviewMinimumBattery,
            WebPreloadEnvironment(
                unmeteredConnection = AndroidNetworkStatus.isUnmetered(context),
                batteryPercent = AndroidSettingsResources.batteryPercent(context),
            ),
        )
    }

    private fun archiveRedirectUrl(context: Context, url: String?): String? =
        WebContentPolicy.resolveUrl(url, readingPreferences.archiveRedirectDomains)
            ?.takeIf { it.archiveRedirected }
            ?.loadUrl

    private fun isCurrentWebViewCallback(view: WebView?): Boolean {
        return view != null && view === webView && coordinator.context != null && coordinator.view != null && webViewBackdrop != null
    }

    private fun beginWebViewLoad(view: WebView, url: String?) {
        if (view !== webView || coordinator.context == null || coordinator.view == null) {
            return
        }

        if (!isPdfViewerUrl(url)) {
            pdfWebViewSession.revokeBridge(view)
        }

        val loadStart = webContentSession.onLoadStarted(
            pageEligible = WebContentPagePolicy.isReaderEligible(url, WEB_CONTENT_URLS),
            nowMillis = SystemClock.uptimeMillis(),
        )
        val generation = loadStart.generation
        lastPageFinishedGeneration = -1
        applyReaderModeChange(loadStart.readerMode)
        webContentDriver.publish(url = url, loading = true, pageReady = false)

        webViewBackdrop?.apply {
            removeCallbacks(webViewBackdropFadeInRunnable)
            animate().cancel()
            alpha = 0f
            visibility = View.VISIBLE
            postDelayed(webViewBackdropFadeInRunnable, 2000)
        }

        showWebViewProgress(0)

        webViewHandler.postDelayed(
            Runnable { handleWebViewLoadTimeout(view, url, generation) },
            WebContentTiming.LOAD_TIMEOUT_MILLIS
        )
    }

    private fun handleWebViewLoadTimeout(view: WebView, url: String?, generation: Int) {
        if (view !== webView || !webContentLoad.isActive(generation)) {
            return
        }

        if (webContentLoad.state.committedVisible) {
            finishWebViewLoadUi(view, generation, false)
            return
        }

        showCustomErrorPage(
            view,
            if (!TextUtils.isEmpty(url)) url else view.getUrl(),
            WebContentFailure.GENERIC
        )
    }

    private fun scheduleVisibleCommitSettle(view: WebView) {
        val generation = webContentLoad.state.generation
        webViewHandler.postDelayed(Runnable {
            if (webContentLoad.shouldSettleCommittedLoad(generation)) {
                finishWebViewLoadUi(view, generation, false)
            }
        }, WebContentTiming.VISIBLE_LOAD_GRACE_MILLIS)
    }

    private fun showWebViewProgress(progress: Int) {
        val currentProgressIndicator = progressIndicator
        if (currentProgressIndicator == null) {
            return
        }

        cancelProgressAnimator()
        currentProgressIndicator.setProgress(progress)
        currentProgressIndicator.setVisibility(View.VISIBLE)
    }

    private fun updateWebViewProgress(view: WebView, newProgress: Int) {
        val currentProgressIndicator = progressIndicator
        if (view !== webView || coordinator.context == null || coordinator.view == null || currentProgressIndicator == null) {
            return
        }
        cancelProgressAnimator()

        val current = currentProgressIndicator.getProgress()
        if (newProgress > current) {
            val animator = ValueAnimator.ofInt(current, newProgress)
            progressAnimator = animator
            animator.duration = 400
            animator.addUpdateListener(AnimatorUpdateListener { anim: ValueAnimator? ->
                if (progressAnimator !== anim || progressIndicator !== currentProgressIndicator) {
                    return@AnimatorUpdateListener
                }
                val animatedValue = checkNotNull(anim).animatedValue as Int
                currentProgressIndicator.progress = animatedValue
            })
            animator.start()
        } else {
            currentProgressIndicator.setProgress(newProgress)
        }

        if (newProgress >= 100) {
            finishWebViewLoadUi(view, webContentLoad.state.generation, true)
        } else if (!webContentLoad.state.uiSettled) {
            currentProgressIndicator.setVisibility(View.VISIBLE)
        }
    }

    private fun finishWebViewLoadUi(view: WebView, generation: Int, completeProgress: Boolean) {
        if (view !== webView || !webContentController.onLoadFinished(generation)) {
            return
        }
        webContentDriver.publish(
            url = view.url,
            loading = false,
            pageReady = true,
            canGoBack = view.canGoBack(),
            showingError = showingErrorPage,
            showingCachedContent = showingCachedArticlePage,
        )

        view.setBackgroundColor(Color.WHITE)
        hideWebViewLoadingBackdrop()

        val currentProgressIndicator = progressIndicator
        if (currentProgressIndicator != null) {
            cancelProgressAnimator()
            if (completeProgress) {
                currentProgressIndicator.setProgress(100)
            }
            currentProgressIndicator.setVisibility(View.GONE)
        }

    }

    private fun hideWebViewLoadingBackdrop() {
        webViewBackdrop?.apply {
            removeCallbacks(webViewBackdropFadeInRunnable)
            animate().cancel()
            visibility = View.GONE
            alpha = 0f
        }
    }

    private fun showCustomErrorPage(
        view: WebView?,
        failingUrl: String?,
        errorPageType: WebContentFailure
    ) {
        val currentWebView = view
        if (!isCurrentWebViewCallback(currentWebView) ||
            currentWebView == null ||
            showingErrorPage ||
            showingCachedArticlePage
        ) {
            return
        }
        linkPreviewController.onWebViewOfflineFallback(coordinator.context)
        val failure = webContentSession.planFailure(
            failure = errorPageType,
            failingUrl = failingUrl,
            currentUrl = currentWebView.url?.takeUnless(::isErrorPageUrl),
        )
        if (failure.tryCachedArticle
            && loadCachedArticleSnapshot(currentWebView, failure.failedUrl)
        ) {
            return
        }
        currentWebView.stopLoading()
        finishWebViewLoadUi(currentWebView, webContentLoad.state.generation, false)
        clearWebViewHistoryOnNextFinish = !currentWebView.canGoBack()
        webContentSession.showError(failure)
        loadUrl(WebContentPagePolicy.errorPageUrl(errorPageType, WEB_CONTENT_URLS))
    }

    fun hideCustomView(notifyCallback: Boolean) {
        if (!this.isShowingCustomView) {
            return
        }

        val currentCustomView = customView
        val currentCustomViewCallback = customViewCallback
        customView = null
        customViewCallback = null

        if (currentCustomView != null && currentCustomView.getParent() is ViewGroup) {
            (currentCustomView.getParent() as ViewGroup).removeView(currentCustomView)
        }

        checkNotNull(fullscreenContainer).apply {
            removeAllViews()
            visibility = View.GONE
        }
        checkNotNull(webViewContainer).visibility = View.VISIBLE
        callbacks.onFullscreenChanged(false)

        setFullscreenSystemBarsHidden(false)
        callbacks.syncOnBackPressedCallbackEnabledState()


        if (notifyCallback && currentCustomViewCallback != null) {
            currentCustomViewCallback.onCustomViewHidden()
        }
    }

    fun loadStoryUrl() {
        webContentController.load(story?.url, readingPreferences.archiveRedirectDomains)
    }

    private fun showCustomView(view: View, callback: CustomViewCallback) {
        if (this.isShowingCustomView) {
            hideCustomView(true)
        }

        customView = view
        customViewCallback = callback

        if (view.getParent() is ViewGroup) {
            (view.getParent() as ViewGroup).removeView(view)
        }

        val currentFullscreenContainer = checkNotNull(fullscreenContainer)
        currentFullscreenContainer.removeAllViews()
        currentFullscreenContainer.addView(
            view, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        currentFullscreenContainer.visibility = View.VISIBLE
        checkNotNull(webViewContainer).visibility = View.GONE
        callbacks.onFullscreenChanged(true)

        setFullscreenSystemBarsHidden(true)
        callbacks.syncOnBackPressedCallbackEnabledState()

    }

    private fun setFullscreenSystemBarsHidden(hidden: Boolean) {
        if (coordinator.getActivity() == null) {
            return
        }

        val windowInsetsController =
            ViewCompat.getWindowInsetsController(
                coordinator.requireActivity().getWindow().getDecorView()
            )
        if (windowInsetsController == null) {
            return
        }

        if (hidden) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun isErrorPageUrl(url: String?): Boolean {
        return WebContentPagePolicy.classify(url, WEB_CONTENT_URLS) ==
            WebContentPageKind.ERROR_PAGE
    }

    private fun loadUrl(url: String?, pdfFilePath: String? = null) {
        var targetUrl = url
        var targetPdfFilePath = pdfFilePath
        var context = coordinator.context
        if (webView == null && integratedWebview) {
            initialize()
            context = coordinator.context
        }
        val targetWebView = webView ?: return
        if (context == null || coordinator.view == null || targetUrl.isNullOrEmpty()) return

        val archiveRedirectUrl = archiveRedirectUrl(context, targetUrl)
        if (archiveRedirectUrl != null) {
            targetUrl = archiveRedirectUrl
        }

        val route = WebContentPagePolicy.route(
            url = targetUrl,
            requestedPdfReference = targetPdfFilePath,
            currentPdfReference = pdfWebViewSession.currentFilePath,
            platformUrls = WEB_CONTENT_URLS,
        ) ?: run {
            webContentSession.finishRetry()
            return
        }
        targetUrl = route.url
        targetPdfFilePath = route.pdfReference

        if (route.kind != WebContentPageKind.ERROR_PAGE) {
            webContentSession.recordRequestedUrl(targetUrl)
            if (route.kind != WebContentPageKind.PDF_VIEWER) {
                pdfWebViewSession.clearCurrentFileReference()
            }
        }
        if (route.kind == WebContentPageKind.PDF_VIEWER) {
            val resolvedPdfFilePath = checkNotNull(targetPdfFilePath)
            applyReaderModeChange(webContentSession.setReaderUnavailableNow())
            pdfWebViewSession.attach(targetWebView, resolvedPdfFilePath)
        } else {
            pdfWebViewSession.revokeBridge(targetWebView)
        }

        targetUrl = linkPreviewController.prepareWebViewLoad(context, targetWebView, targetUrl)
        if (targetUrl.isEmpty()) {
            pdfWebViewSession.revokeBridge(targetWebView)
            return
        }
        beginWebViewLoad(targetWebView, targetUrl)
        targetWebView.loadUrl(targetUrl)
        if (WebContentPagePolicy.classify(targetUrl, WEB_CONTENT_URLS) ==
            WebContentPageKind.ERROR_PAGE
        ) {
            webContentSession.showError()
        }
    }

    private val orInflateWebView: WebView?
        get() {
            if (webView != null) {
                return webView
            }
            if (webViewContainer == null) {
                return null
            }
            val createdWebView = try {
                WebView(checkNotNull(webViewContainer).context).apply {
                    id = R.id.comments_webview
                }
            } catch (exception: RuntimeException) {
                Log.e("MY_APP_TAG", "The embedded browser is unavailable", exception)
                coordinator.showMessage(
                    "Embedded browser unavailable. Check Android System WebView and try again",
                )
                return null
            }
            webView = createdWebView
            attachWebView(createdWebView)
            return createdWebView
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachWebView(view: WebView) {
        val container = webViewContainer ?: return
        view.setOnTouchListener(OnTouchListener { ignored: View?, event: MotionEvent? ->
            if (event == null) return@OnTouchListener false
            if (predictiveBackScrollFrozen) {
                restorePredictiveBackScroll()
                return@OnTouchListener true
            }
            when (event.getActionMasked()) {
                MotionEvent.ACTION_DOWN -> {
                    touchGestureStartScrollX = view.getScrollX()
                    touchGestureStartScrollY = view.getScrollY()
                    touchGestureStartScrollCaptured = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchGestureStartScrollCaptured = false
                }
            }
            false
        })
        container.addView(
            view,
            min(1, container.childCount),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun downloadPdf(
        url: String?,
        contentDisposition: String?,
        mimetype: String?,
        ctx: Context?
    ) {
        if (ctx == null) {
            return
        }
        coordinator.showMessage("Loading PDF...", UserMessageDuration.LONG)
        coroutineScope.launch {
            runCatching { pdfDownloads.download(url) }
                .onSuccess { filePath -> loadUrl(PDF_LOADER_URL, filePath) }
                .onFailure {
                showDownloadButton(url, contentDisposition, mimetype)
                }
        }
    }

    private fun showDownloadButton(url: String?, contentDisposition: String?, mimetype: String?) {
        val currentWebView = webView ?: return
        val currentDownloadButton = downloadButton ?: return
        currentWebView.visibility = View.GONE
        currentDownloadButton.visibility = View.VISIBLE
        currentDownloadButton.setOnClickListener(object : View.OnClickListener {
                override fun onClick(view: View) {
                    try {
                        val request = DownloadManager.Request(Uri.parse(url))

                        request.allowScanningByMediaScanner()
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        request.setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            URLUtil.guessFileName(url, contentDisposition, mimetype)
                        )
                        val dm = view.getContext()
                            .getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        dm.enqueue(request)
                        coordinator.showMessage("Downloading...", UserMessageDuration.LONG)
                    } catch (e: Exception) {
                        coordinator.showMessage(
                            "Failed to download, opening in browser",
                            UserMessageDuration.LONG,
                        )
                        if (!AndroidExternalLinkLauncher.openExternalBrowser(
                                coordinator.requireActivity(),
                                com.simon.harmonichackernews.platform.ExternalLinkRequest(
                                    url.orEmpty(),
                                    preferInApp = false,
                                ),
                            )
                        ) {
                            coordinator.showMessage(WebContentCopy.DOWNLOAD_LINK_FAILED)
                        }
                    }
                }
        })
    }

    fun requestSummary(callback: PageTextCallback) {
        if (webView == null) initialize()
        if (webView == null || !startedLoading) {
            startedLoading = true
            loadUrl(story?.url)
        }

        if (webView == null) {
            webViewHandler.post { callback.onResult(null) }
            return
        }

        pendingSummaryCallback = callback
        webContentSession.beginPageTextRequest()

        val currentWebView = webView
        if (lastPageFinishedGeneration == webContentLoad.state.generation) {
            completePendingSummaryIfReady(currentWebView)
        }
    }

    private fun completePendingSummaryIfReady(targetWebView: WebView?) {
        val callback = pendingSummaryCallback
        if (callback == null || targetWebView == null || targetWebView !== webView) {
            return
        }

        val generation = webContentSession.currentPageTextRequestGeneration()
        val readStarted = webContentSession.readRequestedPageText(generation) { result ->
            finishPendingSummary(generation, result, callback)
        }
        if (readStarted) {
            // Loading the article has its own WebView lifecycle timeout. Only start the extraction
            // timeout after onPageFinished; otherwise a slow page can lose the retry before it has
            // produced any DOM text at all.
            webViewHandler.postDelayed(
                Runnable { finishPendingSummary(generation, "", null) },
                WebContentTiming.SUMMARY_LOAD_TIMEOUT_MILLIS,
            )
        }
    }

    private fun canReadLoadedPageText(targetWebView: WebView?): Boolean {
        return targetWebView != null && targetWebView === webView && startedLoading
                && !webContentLoad.state.inProgress && targetWebView.getProgress() >= 100 && !showingErrorPage && !TextUtils.isEmpty(
            targetWebView.getUrl()
        ) && !isErrorPageUrl(targetWebView.getUrl())
    }

    private fun finishPendingSummary(
        generation: Int,
        pageText: String?,
        completedCallback: PageTextCallback?
    ) {
        val callback = completedCallback ?: pendingSummaryCallback
        if (callback == null || !webContentSession.claimPageTextRequest(generation)) {
            return
        }
        pendingSummaryCallback = null
        webViewHandler.post { callback.onResult(pageText) }
    }

    private fun getCustomErrorPageType(errorCode: Int): WebContentFailure? {
        when (errorCode) {
            WebViewClient.ERROR_HOST_LOOKUP -> return WebContentFailure.DNS
            WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT -> return WebContentFailure.OFFLINE
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> return WebContentFailure.SSL
            WebViewClient.ERROR_AUTHENTICATION, WebViewClient.ERROR_BAD_URL, WebViewClient.ERROR_FILE, WebViewClient.ERROR_FILE_NOT_FOUND, WebViewClient.ERROR_IO, WebViewClient.ERROR_PROXY_AUTHENTICATION, WebViewClient.ERROR_REDIRECT_LOOP, WebViewClient.ERROR_UNKNOWN, WebViewClient.ERROR_TOO_MANY_REQUESTS, WebViewClient.ERROR_UNSUPPORTED_AUTH_SCHEME, WebViewClient.ERROR_UNSUPPORTED_SCHEME -> return WebContentFailure.GENERIC
            else -> return null
        }
    }

    private fun loadCachedArticleSnapshot(view: WebView?, failingUrl: String?): Boolean {
        val context = coordinator.context
        val currentStory = story
        if (view == null || context == null || coordinator.view == null ||
            currentStory == null || !currentStory.isLink || currentStory.id <= 0
        ) return false

        val html = storyCache.loadArticle(currentStory.id)
            ?.takeUnless(String::isEmpty)
            ?: return false

        val baseUrl = WebContentPagePolicy.cachedArticleBaseUrl(
            storedSourceUrl = storyCache.articleUrl(currentStory.id),
            failingUrl = failingUrl,
            storyUrl = currentStory.url,
        ) ?: return false

        webContentSession.showCachedContent(failingUrl, baseUrl)
        view.stopLoading()
        clearWebViewHistoryOnNextFinish = true
        coordinator.showMessage(WebContentCopy.SHOWING_CACHED_CONTENT)
        view.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
        return true
    }

    fun destroy() {
        destroy(false)
    }

    private fun destroy(rendererProcessGone: Boolean) {
        cancelProgressAnimator()
        pdfWebViewSession.release(webView, removeJavascriptInterface = !rendererProcessGone)
        webContentSession.reset()
        webContentDriver.publish(WebContentDriverState())
        pendingSummaryCallback = null
        lastPageFinishedGeneration = -1
        webViewHandler.removeCallbacksAndMessages(null)
        linkPreviewController.cancelPendingNitterLinkPreviewRead()
        if (webView != null) {
            val webViewToDestroy = webView ?: return
            webView = null
            initializedWebView = false

            if (!rendererProcessGone) {
                webViewToDestroy.setWebChromeClient(null)
                webViewToDestroy.setDownloadListener(null)
            }

            if (webViewToDestroy.getParent() is ViewGroup) {
                (webViewToDestroy.getParent() as ViewGroup).removeView(webViewToDestroy)
            }

            try {
                if (!rendererProcessGone) {
                    webViewToDestroy.stopLoading()
                    webViewToDestroy.clearHistory()
                    webViewToDestroy.clearCache(true)
                    webViewToDestroy.onPause()
                    webViewToDestroy.removeAllViews()
                    webViewToDestroy.destroyDrawingCache()
                }
                webViewToDestroy.destroy()
            } catch (e: RuntimeException) {
                Log.e("MY_APP_TAG", "Failed to destroy WebView cleanly", e)
            }
        }
    }

    private fun isPdfViewerUrl(url: String?): Boolean = isTrustedPdfViewerUrl(url, PDF_LOADER_URL)

    private fun restartWebView() {
        val context = coordinator.context
        if (context == null || coordinator.view == null || webViewContainer == null) {
            destroy(true)
            return
        }

        destroy()

        try {
            val recreatedWebView = WebView(context).apply {
                id = R.id.comments_webview
            }
            webView = recreatedWebView
            attachWebView(recreatedWebView)
            initialize()
        } catch (e: RuntimeException) {
            webView = null
            initializedWebView = false
            Log.e("MY_APP_TAG", "Failed to recreate WebView", e)
        }
    }

    fun onDestroyView(rootView: View?) {
        hideCustomView(false)

        downloadButton?.setOnClickListener(null)
        webViewBackdrop?.apply {
            removeCallbacks(webViewBackdropFadeInRunnable)
            animate().cancel()
        }
        destroy()
    }

    private fun cancelProgressAnimator() {
        progressAnimator?.cancel()
        progressAnimator = null
    }

    fun clearViewReferences() {
        pdfWebViewSession.release(webView, removeJavascriptInterface = true)
        webView = null
        webViewContainer = null
        fullscreenContainer = null
        webViewBackdrop = null
        downloadButton = null
        progressIndicator = null
        customView = null
        customViewCallback = null
        webContentDriver.publish(WebContentDriverState())
    }

    /** Native WebView adapter for the shared web-content controller. */
    private inner class AndroidWebContentDriver : WebContentDriver {
        private val mutableState = MutableStateFlow(WebContentDriverState())
        override val state: StateFlow<WebContentDriverState> = mutableState.asStateFlow()

        override fun load(url: String) = loadUrl(url)

        override fun reload() {
            webView?.reload()
        }

        override fun goBack(): Boolean {
            val view = webView?.takeIf { it.canGoBack() } ?: return false
            view.goBack()
            return true
        }

        override fun evaluateJavaScript(script: String, onResult: (String?) -> Unit) {
            webView?.evaluateJavascript(script, ValueCallback { onResult(it) }) ?: onResult(null)
        }

        override fun readPageText(onResult: (String?) -> Unit) {
            val targetWebView = webView
            if (!canReadLoadedPageText(targetWebView)) {
                onResult(null)
                return
            }
            val generation = webContentLoad.state.generation
            checkNotNull(targetWebView).evaluateJavascript(
                WebContentPageText.READ_COMMAND,
            ) { result ->
                if (generation != webContentLoad.state.generation ||
                    !canReadLoadedPageText(targetWebView)
                ) {
                    onResult(null)
                } else {
                    onResult(WebContentPageText.decode(result))
                }
            }
        }

        fun currentState(): WebContentDriverState = mutableState.value.copy(
            currentUrl = webView?.url,
            canGoBack = webView?.canGoBack() == true,
            showingError = showingErrorPage,
            showingCachedContent = showingCachedArticlePage,
        )

        fun publish(state: WebContentDriverState) {
            mutableState.value = state
        }

        fun publish(
            url: String? = currentState().currentUrl,
            loading: Boolean = currentState().loading,
            pageReady: Boolean = currentState().pageReady,
            canGoBack: Boolean = currentState().canGoBack,
            showingError: Boolean = currentState().showingError,
            showingCachedContent: Boolean = currentState().showingCachedContent,
        ) {
            mutableState.value = WebContentDriverState(
                currentUrl = url,
                loading = loading,
                pageReady = pageReady,
                canGoBack = canGoBack,
                showingError = showingError,
                showingCachedContent = showingCachedContent,
            )
        }
    }

    // The AndroidX detector does not recognize this Kotlin override further below.
    @SuppressLint("MissingOnRenderProcessGone")
    private inner class MyWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            val currentView = view
            if (!isCurrentWebViewCallback(currentView) || currentView == null) {
                return
            }
            beginWebViewLoad(currentView, url)
            if (!isErrorPageUrl(url)) {
                webContentSession.recordRequestedUrl(
                    url,
                    preservePage = showingCachedArticlePage,
                )
            }
        }

        override fun onPageCommitVisible(view: WebView?, url: String?) {
            super.onPageCommitVisible(view, url)
            val currentView = view
            if (!isCurrentWebViewCallback(currentView) || currentView == null) {
                return
            }
            webContentController.onPageCommitVisible()
            scheduleVisibleCommitSettle(currentView)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            val currentView = view
            if (!isCurrentWebViewCallback(currentView) || currentView == null) {
                return
            }
            val finishedGeneration = webContentLoad.state.generation
            lastPageFinishedGeneration = finishedGeneration
            finishWebViewLoadUi(currentView, finishedGeneration, true)

            callbacks.syncOnBackPressedCallbackEnabledState()

            if (clearWebViewHistoryOnNextFinish) {
                clearWebViewHistoryOnNextFinish = false
                currentView.post {
                    if (isCurrentWebViewCallback(currentView)) {
                        currentView.clearHistory()
                        callbacks.syncOnBackPressedCallbackEnabledState()
                    }
                }
            }

            linkPreviewController.onWebViewPageFinished(coordinator.context, view, url)

            val pageEligible = !showingErrorPage &&
                WebContentPagePolicy.isReaderEligible(url, WEB_CONTENT_URLS)
            val readerResult = webContentSession.onPageFinished(pageEligible)
            applyReaderModeChange(readerResult.change)
            when (val decision = readerResult.decision) {
                is ReaderModePageDecision.Apply -> view.post(Runnable {
                    if (isCurrentWebViewCallback(view)) {
                        applyReaderMode(decision.enabled, decision.showFeedback)
                    }
                })
                ReaderModePageDecision.CheckAvailability ->
                    view.post(Runnable { checkReaderModeAvailability(view, finishedGeneration) })
                ReaderModePageDecision.None -> Unit
            }
            webViewHandler.postDelayed(
                Runnable {
                    if (lastPageFinishedGeneration == finishedGeneration) {
                        completePendingSummaryIfReady(currentView)
                    }
                },
                WebContentTiming.SUMMARY_PAGE_TEXT_SETTLE_MILLIS,
            )
        }

        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            if (!isPdfViewerUrl(url)) {
                pdfWebViewSession.revokeBridge(view)
            }

            if (url.startsWith("intent://")) {
                try {
                    val context = view.getContext()
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)

                    val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                    if (fallbackUrl != null) {
                        val archiveRedirectUrl =
                            archiveRedirectUrl(context, fallbackUrl)
                        loadUrl(if (archiveRedirectUrl != null) archiveRedirectUrl else fallbackUrl)
                        return true
                    } else {
                        if (intent.resolveActivity(context.getPackageManager()) != null) {
                            context.startActivity(intent)
                            return true
                        }
                    }
                } catch (e: Exception) {
                    return false
                }
            }

            val archiveRedirectUrl = archiveRedirectUrl(view.context, url)
            if (archiveRedirectUrl != null) {
                loadUrl(archiveRedirectUrl)
                return true
            }

            return false
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest?
        ): Boolean {
            return request != null && request.getUrl() != null && shouldOverrideUrlLoading(
                view,
                request.getUrl().toString()
            )
        }

        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest
        ): WebResourceResponse? {
            if (!this@CommentsWebViewController.isBlockingAds) {
                return super.shouldInterceptRequest(view, request)
            }
            val EMPTY = ByteArrayInputStream("".toByteArray())
            if (!adBlocklist.hosts.value.isEmpty) {
                val host = request.getUrl().getHost()
                if (host != null && adBlocklist.contains(host)) {
                    HarmonicLog.debug("Blocked: " + request.getUrl())
                    return WebResourceResponse("text/plain", "utf-8", EMPTY)
                }
            }

            return super.shouldInterceptRequest(view, request)
        }

        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail): Boolean {
            val wasCurrentWebView = view === webView
            if (wasCurrentWebView) {
                destroy(true)
            }

            if (coordinator.context == null || coordinator.view == null || webViewContainer == null) {
                return true
            }

            if (!detail.didCrash()) {
                Log.e(
                    "MY_APP_TAG", "System killed the WebView rendering process " +
                            "to reclaim memory. Recreating..."
                )

                if (wasCurrentWebView) {
                    restartWebView()
                }

                return true
            }
            val context = coordinator.context
            if (context != null && wasCurrentWebView) {
                coordinator.showMessage("WebView crashed, reinitializing")
                restartWebView()
            }

            Log.e("MY_APP_TAG", "The WebView rendering process crashed!")
            return true
        }

        @Suppress("deprecation")
        override fun onReceivedError(
            view: WebView?,
            errorCode: Int,
            description: String?,
            failingUrl: String?
        ) {
            val errorPageType = getCustomErrorPageType(errorCode)
            if (errorPageType != null) {
                showCustomErrorPage(view, failingUrl, errorPageType)
            } else {
                super.onReceivedError(view, errorCode, description, failingUrl)
            }
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            val errorPageType = getCustomErrorPageType(error.getErrorCode())
            if (request.isForMainFrame() && errorPageType != null) {
                showCustomErrorPage(
                    view,
                    if (request.getUrl() != null) request.getUrl().toString() else null,
                    errorPageType
                )
            } else {
                super.onReceivedError(view, request, error)
            }
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            if (request != null && request.isForMainFrame()) {
                val failingUrl = if (request.getUrl() != null) request.getUrl().toString() else null
                val statusCode = if (errorResponse != null) errorResponse.getStatusCode() else -1
                Log.w("MY_APP_TAG", "WebView HTTP error " + statusCode + " for " + failingUrl)
                showCustomErrorPage(view, failingUrl, WebContentFailure.GENERIC)
            } else {
                super.onReceivedHttpError(view, request, errorResponse)
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError?) {
            handler.cancel()
            val failingUrl = if (error != null) error.getUrl() else null
            if (!TextUtils.isEmpty(failingUrl) && !TextUtils.equals(
                    failingUrl,
                    webContentSession.state.lastRequestedUrl
                ) && !TextUtils.equals(failingUrl, view.getUrl())
            ) {
                return
            }
            showCustomErrorPage(view, failingUrl, WebContentFailure.SSL)
        }
    }

    companion object {
        private const val PDF_MIME_TYPE = "application/pdf"
        private val PDF_LOADER_URL = Res.getUri(sharedWebResource(WebContentAssets.PDF_VIEWER_INDEX))
        private val OFFLINE_PAGE_URL = Res.getUri(sharedWebResource(WebContentAssets.OFFLINE_PAGE))
        private val WEB_CONTENT_URLS = WebContentPlatformUrls(PDF_LOADER_URL, OFFLINE_PAGE_URL)

        private fun sharedWebResource(path: String): String = "files/web/$path"
    }
}
