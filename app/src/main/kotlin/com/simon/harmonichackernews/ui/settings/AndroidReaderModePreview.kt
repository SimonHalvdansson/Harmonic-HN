package com.simon.harmonichackernews.ui.settings

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.simon.harmonichackernews.AndroidReaderModeResources
import com.simon.harmonichackernews.awaitAndroidWebViewBackgroundStartup
import com.simon.harmonichackernews.awaitAndroidWebViewStartup
import com.simon.harmonichackernews.settings.ReadingPreferences
import com.simon.harmonichackernews.ui.navigation.ActivityNavigationTransitionDurationMillis
import com.simon.harmonichackernews.ui.reader.ReaderPreviewDocument
import com.simon.harmonichackernews.ui.theme.cardBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** A local document uses browser CSS pixels and the same font bytes as the article reader. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun AndroidReaderModePreview(reading: ReadingPreferences) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val background = colors.cardBackground.toArgb()
    val foreground = colors.onSurface.toArgb()
    var html by remember { mutableStateOf<String?>(null) }
    var height by remember { mutableStateOf(InitialPreviewHeight) }
    var started by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    val displayedHeight = remember { Animatable(InitialPreviewHeight.value) }
    var revealed by remember { mutableStateOf(false) }
    val currentReading by rememberUpdatedState(reading)
    val previewAlpha by animateFloatAsState(if (ready) 1f else 0f, tween(180), label = "reader preview reveal")

    LaunchedEffect(height, ready) {
        if (!ready) {
            revealed = false
        } else if (!revealed) {
            // Keep the blank card stable until fonts and pixels are ready, then resize with the fade.
            displayedHeight.animateTo(height.value, tween(180))
            revealed = true
        } else {
            // CSS already animates line spacing; follow each measured frame without a second easing.
            displayedHeight.snapTo(height.value)
        }
    }

    LaunchedEffect(Unit) {
        try {
            coroutineScope {
                // Prepare the provider during navigation, but defer its main-thread work until settled.
                val navigationFinished = async {
                    val durationScale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
                    withFrameNanos { }
                    delay((ActivityNavigationTransitionDurationMillis * durationScale).toLong())
                }
                awaitAndroidWebViewBackgroundStartup(context)
                navigationFinished.await()
                awaitAndroidWebViewStartup(context)
            }
            started = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("ReaderPreview", "Unable to start reader preview", error)
            failed = true
        }
    }

    LaunchedEffect(reading.readerModeFont, background, foreground) {
        val theme = AndroidReaderModeResources.theme(context, reading)
        html = withContext(Dispatchers.Default) {
            ReaderPreviewDocument.html(
                reading.readerModeFont.storedValue, theme.fontFaceCss, background, foreground,
                "PreviewSize.changed(height);",
                reading.readerModeFontSize, reading.readerModeLineHeight.multiplier,
            )
        }
    }
    Box(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth().height(displayedHeight.value.dp).clip(RoundedCornerShape(20.dp))
            .background(colors.cardBackground),
    ) {
        if (started && html != null) AndroidView(
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = previewAlpha },
            factory = { previewContext ->
                WebView(previewContext).apply {
                    settings.javaScriptEnabled = true // Only the bundled size observer runs here.
                    settings.blockNetworkLoads = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    addJavascriptInterface(PreviewSize(
                        onChanged = { cssPixels ->
                            post { if (tag != null) height = cssPixels.toFloat().coerceIn(1f, 2000f).dp }
                        },
                        onReady = {
                            post {
                                if (tag != null) postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                                    override fun onComplete(requestId: Long) {
                                        if (tag != null) ready = true
                                    }
                                })
                            }
                        },
                    ), "PreviewSize")
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                        override fun onPageFinished(view: WebView, url: String) {
                            view.updateReaderSize(currentReading)
                            view.evaluateJavascript("document.fonts.ready.then(function(){PreviewSize.ready();});", null)
                        }
                    }
                }
            },
            update = { view ->
                view.setBackgroundColor(background)
                val document = html
                if (document != null && view.tag != document) {
                    ready = false
                    view.tag = document
                    view.loadDataWithBaseURL("https://reader-preview.invalid/", document, "text/html", "UTF-8", null)
                }
                view.updateReaderSize(currentReading)
            },
            onRelease = { view ->
                view.tag = null
                view.removeJavascriptInterface("PreviewSize")
                view.stopLoading()
                view.destroy()
            },
        )
        if (failed) {
            Text(
                "Reader preview is unavailable",
                modifier = Modifier.align(Alignment.Center).padding(20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

private val InitialPreviewHeight = 200.dp

private fun WebView.updateReaderSize(reading: ReadingPreferences) {
    evaluateJavascript(ReaderPreviewDocument.sizeScript(
        reading.readerModeFontSize, reading.readerModeLineHeight.multiplier,
    ), null)
}

private class PreviewSize(private val onChanged: (Double) -> Unit, private val onReady: () -> Unit) {
    @JavascriptInterface
    fun changed(cssPixels: Double) {
        if (cssPixels.isFinite() && cssPixels > 0) onChanged(cssPixels)
    }

    @JavascriptInterface
    fun ready() = onReady()
}
