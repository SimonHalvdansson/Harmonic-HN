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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.simon.harmonichackernews.ui.comments.CommentsComposeController
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.accessibilityLabel
import platform.UIKit.systemBackgroundColor
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

/** iOS-native browser retained for the lifetime of one comments destination. */
@OptIn(ExperimentalForeignApi::class)
internal class IosCommentsWebView(initialUrl: String) {
    private val initialUrl = initialUrl
    private var loadedUrl: String? = null
    private var inverted = false

    val view: WKWebView = WKWebView(
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
    }

    fun ensureLoaded() {
        if (loadedUrl == null) load(initialUrl)
    }

    fun load(url: String) {
        val nativeUrl = NSURL(string = url)
        loadedUrl = url
        view.loadRequest(NSMutableURLRequest.requestWithURL(URL = nativeUrl))
    }

    fun reload() {
        if (loadedUrl == null) ensureLoaded() else view.reload()
    }

    fun currentUrl(): String = view.URL?.absoluteString ?: loadedUrl ?: initialUrl

    fun canGoBack(): Boolean = view.canGoBack

    fun goBack() {
        if (view.canGoBack) view.goBack()
    }

    fun updateAppearance(dark: Boolean, matchTheme: Boolean) {
        view.overrideUserInterfaceStyle = if (matchTheme && dark) {
            UIUserInterfaceStyle.UIUserInterfaceStyleDark
        } else {
            UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
    }

    fun toggleInversion() {
        inverted = !inverted
        val filter = if (inverted) "invert(1) hue-rotate(180deg)" else "none"
        view.evaluateJavaScript(
            "document.documentElement.style.filter='$filter';" +
                "document.documentElement.style.backgroundColor='transparent';",
            completionHandler = null,
        )
    }

    fun dispose() {
        view.stopLoading()
        view.navigationDelegate = null
        view.UIDelegate = null
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
            UIKitView(
                factory = {
                    webView.ensureLoaded()
                    webView.view
                },
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
