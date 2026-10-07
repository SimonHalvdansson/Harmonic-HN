package com.simon.harmonichackernews.ios

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.simon.harmonichackernews.settings.ReadingPreferences
import com.simon.harmonichackernews.ui.reader.ReaderModeResources
import com.simon.harmonichackernews.ui.reader.ReaderPreviewDocument
import com.simon.harmonichackernews.ui.theme.cardBackground
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSNumber
import platform.WebKit.*
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun IosReaderModePreview(reading: ReadingPreferences) {
    val colors = MaterialTheme.colorScheme
    val background = colors.cardBackground.toArgb()
    val foreground = colors.onSurface.toArgb()
    var html by remember { mutableStateOf<String?>(null) }
    var height by remember { mutableStateOf(240.dp) }
    val sizeScript by rememberUpdatedState(ReaderPreviewDocument.sizeScript(
        reading.readerModeFontSize, reading.readerModeLineHeight.multiplier,
    ))
    val delegate = remember {
        ReaderPreviewDelegate(
            onHeight = { if (it.isFinite() && it > 0) height = it.toFloat().coerceIn(1f, 2000f).dp },
            sizeScript = { sizeScript },
        )
    }
    LaunchedEffect(reading.readerModeFont, background, foreground) {
        val font = reading.readerModeFont.storedValue
        html = ReaderPreviewDocument.html(font, ReaderModeResources.fontData(font)?.fontFaceCss.orEmpty(),
            background, foreground, "window.webkit.messageHandlers.previewSize.postMessage(height);")
    }
    var loadedHtml by remember { mutableStateOf<String?>(null) }
    UIKitView(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth().height(height).clip(RoundedCornerShape(20.dp)),
        factory = {
            WKWebView(CGRectZero.readValue(), WKWebViewConfiguration().apply {
                websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
                userContentController.addScriptMessageHandler(delegate, "previewSize")
            }).apply {
                navigationDelegate = delegate
                scrollView.scrollEnabled = false
                setOpaque(false)
            }
        },
        update = { view ->
            html?.let { document ->
                if (loadedHtml != document) {
                    loadedHtml = document
                    view.loadHTMLString(document, baseURL = null)
                }
            }
            view.evaluateJavaScript(sizeScript, null)
        },
        onRelease = {
            it.stopLoading()
            it.navigationDelegate = null
            it.configuration.userContentController.removeScriptMessageHandlerForName("previewSize")
        },
        properties = UIKitInteropProperties(interactionMode = null, isNativeAccessibilityEnabled = true),
    )
}

private class ReaderPreviewDelegate(
    val onHeight: (Double) -> Unit,
    val sizeScript: () -> String,
) : NSObject(), WKScriptMessageHandlerProtocol, WKNavigationDelegateProtocol {
    override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
        (didReceiveScriptMessage.body as? NSNumber)?.doubleValue?.let(onHeight)
    }

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        webView.evaluateJavaScript(sizeScript(), null)
    }
}
