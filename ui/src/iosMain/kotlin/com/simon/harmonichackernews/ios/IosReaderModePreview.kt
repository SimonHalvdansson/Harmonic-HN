package com.simon.harmonichackernews.ios

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.simon.harmonichackernews.settings.ReadingPreferences
import com.simon.harmonichackernews.ui.reader.ReaderModeResources
import com.simon.harmonichackernews.ui.reader.ReaderPreviewDocument
import com.simon.harmonichackernews.ui.navigation.ActivityNavigationTransitionDurationMillis
import com.simon.harmonichackernews.ui.theme.cardBackground
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSNumber
import platform.UIKit.UIColor
import platform.WebKit.*
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun IosReaderModePreview(reading: ReadingPreferences) {
    val colors = MaterialTheme.colorScheme
    val background = colors.cardBackground.toArgb()
    val foreground = colors.onSurface.toArgb()
    var html by remember { mutableStateOf<String?>(null) }
    var height by remember { mutableStateOf(200.dp) }
    var ready by remember { mutableStateOf(false) }
    var navigationFinished by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    val displayedHeight = remember { Animatable(200f) }
    val revealReady = ready && navigationFinished
    val previewAlpha = remember { Animatable(0f) }
    LaunchedEffect(revealReady) {
        if (revealReady) previewAlpha.animateTo(1f, tween(180)) else previewAlpha.snapTo(0f)
    }
    LaunchedEffect(Unit) {
        val durationScale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
        withFrameNanos { }
        delay((ActivityNavigationTransitionDurationMillis * durationScale).toLong())
        navigationFinished = true
    }
    LaunchedEffect(height, revealReady) {
        if (!revealReady) {
            revealed = false
        } else if (!revealed) {
            // Match Android: keep the placeholder stable until fonts and pixels are ready.
            displayedHeight.animateTo(height.value, tween(180))
            revealed = true
        } else {
            // CSS animates line spacing; follow its measured frames without another easing.
            displayedHeight.snapTo(height.value)
        }
    }
    val sizeScript by rememberUpdatedState(ReaderPreviewDocument.sizeScript(
        reading.readerModeFontSize, reading.readerModeLineHeight.multiplier,
    ))
    val delegate = remember {
        ReaderPreviewDelegate(
            onHeight = { if (it.isFinite() && it > 0) height = it.toFloat().coerceIn(1f, 2000f).dp },
            onReady = { ready = true },
            sizeScript = { sizeScript },
        )
    }
    LaunchedEffect(reading.readerModeFont, background, foreground) {
        val font = reading.readerModeFont.storedValue
        html = ReaderPreviewDocument.html(font, ReaderModeResources.fontData(font)?.fontFaceCss.orEmpty(),
            background, foreground, "window.webkit.messageHandlers.previewSize.postMessage(height);",
            reading.readerModeFontSize, reading.readerModeLineHeight.multiplier)
    }
    var loadedHtml by remember { mutableStateOf<String?>(null) }
    Box(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth().height(displayedHeight.value.dp).clip(RoundedCornerShape(20.dp))
            .background(colors.cardBackground),
    ) {
        UIKitView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                WKWebView(CGRectZero.readValue(), WKWebViewConfiguration().apply {
                    websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
                    userContentController.addScriptMessageHandler(delegate, "previewSize")
                    userContentController.addScriptMessageHandler(delegate, "previewReady")
                }).apply {
                    navigationDelegate = delegate
                    scrollView.scrollEnabled = false
                    setOpaque(false)
                    backgroundColor = UIColor.clearColor
                }
            },
            update = { view ->
                html?.let { document ->
                    if (loadedHtml != document) {
                        ready = false
                        delegate.beginLoad()
                        loadedHtml = document
                        delegate.navigation = view.loadHTMLString(document, baseURL = null)
                    }
                }
                view.evaluateJavaScript(sizeScript, null)
            },
            onRelease = {
                delegate.beginLoad()
                it.stopLoading()
                it.navigationDelegate = null
                it.configuration.userContentController.removeScriptMessageHandlerForName("previewSize")
                it.configuration.userContentController.removeScriptMessageHandlerForName("previewReady")
            },
            properties = UIKitInteropProperties(interactionMode = null, isNativeAccessibilityEnabled = true),
        )
        // UIKitView clears a hole through the Compose canvas. Cover that native surface
        // while loading, then fade the cover so the blank card and web content blend together.
        val contentAlpha = if (revealReady) previewAlpha.value else 0f
        if (contentAlpha < 1f) {
            Box(Modifier.matchParentSize().background(colors.cardBackground.copy(alpha = 1f - contentAlpha)))
        }
    }
}

private class ReaderPreviewDelegate(
    val onHeight: (Double) -> Unit,
    val onReady: () -> Unit,
    val sizeScript: () -> String,
) : NSObject(), WKScriptMessageHandlerProtocol, WKNavigationDelegateProtocol {
    var navigation: WKNavigation? = null
    private var generation = 0

    fun beginLoad() {
        generation++
        navigation = null
    }

    override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
        val value = didReceiveScriptMessage.body as? NSNumber ?: return
        when (didReceiveScriptMessage.name) {
            "previewSize" -> onHeight(value.doubleValue)
            "previewReady" -> if (value.intValue == generation) onReady()
        }
    }

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        if (didFinishNavigation == null || didFinishNavigation != navigation) return
        // Wait for the selected font and two rendering frames, then report the final size
        // before revealing. A superseded document must not reveal a subsequent font reload.
        webView.evaluateJavaScript("""
            ${sizeScript()}
            document.fonts.ready.then(function() {
                requestAnimationFrame(function() { requestAnimationFrame(function() {
                    window.webkit.messageHandlers.previewSize.postMessage(
                        document.getElementById('preview').getBoundingClientRect().height);
                    window.webkit.messageHandlers.previewReady.postMessage($generation);
                }); });
            });
        """.trimIndent(), null)
    }
}
