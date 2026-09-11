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
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

/** Lightweight destination owner; the native browser is created only when loading is requested. */
@OptIn(ExperimentalForeignApi::class)
internal class IosCommentsWebView(initialUrl: String) {
    private val initialUrl = WebContentPolicy.validatedHttpUrl(initialUrl)
    private var loadedUrl: String? = null
    private var inverted = false
    private var appearance = UIUserInterfaceStyle.UIUserInterfaceStyleLight
    private var disposed = false

    var view: WKWebView? by mutableStateOf(null)
        private set

    private fun createView(): WKWebView = WKWebView(
        frame = CGRectZero.readValue(),
        configuration = WKWebViewConfiguration().apply {
            websiteDataStore = platform.WebKit.WKWebsiteDataStore.defaultDataStore()
            defaultWebpagePreferences.allowsContentJavaScript = true
            allowsInlineMediaPlayback = true
        },
    ).apply {
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
        // Match Android: hidden browser startup must not compete with the opening animation.
        delay(ActivityNavigationTransitionDurationMillis.toLong())
        if (WebContentPolicy.shouldPreload(mode, minimumBatteryPercent, environment())) {
            ensureLoaded()
        }
    }

    fun load(url: String): Boolean {
        if (disposed) return false
        val safeUrl = WebContentPolicy.validatedHttpUrl(url) ?: return false
        val nativeUrl = try {
            NSURL(string = safeUrl)
        } catch (_: Exception) {
            return false
        }
        return try {
            val browser = view ?: createView().also { view = it }
            browser.loadRequest(NSMutableURLRequest.requestWithURL(URL = nativeUrl))
            loadedUrl = safeUrl
            true
        } catch (_: Exception) {
            false
        }
    }

    fun reload() {
        if (loadedUrl == null) ensureLoaded() else view?.reload()
    }

    fun currentUrl(): String? = view?.URL?.absoluteString ?: loadedUrl ?: initialUrl

    fun canGoBack(): Boolean = view?.canGoBack == true

    fun goBack() {
        view?.takeIf { it.canGoBack }?.goBack()
    }

    fun updateAppearance(dark: Boolean, matchTheme: Boolean) {
        appearance = if (matchTheme && dark) {
            UIUserInterfaceStyle.UIUserInterfaceStyleDark
        } else {
            UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
        view?.overrideUserInterfaceStyle = appearance
    }

    fun toggleInversion() {
        val browser = view ?: return
        inverted = !inverted
        val filter = if (inverted) "invert(1) hue-rotate(180deg)" else "none"
        browser.evaluateJavaScript(
            "document.documentElement.style.filter='$filter';" +
                "document.documentElement.style.backgroundColor='transparent';",
            completionHandler = null,
        )
    }

    fun dispose() {
        disposed = true
        view?.let {
            it.stopLoading()
            it.navigationDelegate = null
            it.UIDelegate = null
        }
        view = null
    }
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
            val browser = webView.view
            if (browser != null) UIKitView(
                factory = { browser },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = webViewTopInset),
                properties = UIKitInteropProperties(
                    isInteractive = true,
                    isNativeAccessibilityEnabled = true,
                ),
            )
        }
    }
}
