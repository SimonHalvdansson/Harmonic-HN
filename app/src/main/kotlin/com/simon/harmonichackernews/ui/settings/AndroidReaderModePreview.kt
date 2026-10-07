package com.simon.harmonichackernews.ui.settings

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.simon.harmonichackernews.AndroidReaderModeResources
import com.simon.harmonichackernews.presentation.ReaderModeScriptProtocol
import com.simon.harmonichackernews.presentation.ReaderModeSourceAssembler
import com.simon.harmonichackernews.settings.ReadingPreferences
import com.simon.harmonichackernews.ui.theme.cardBackground
import kotlin.math.roundToInt

/** A local document uses browser CSS pixels and the same font bytes as the article reader. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun AndroidReaderModePreview(reading: ReadingPreferences) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val background = colors.cardBackground.toArgb()
    val foreground = colors.onSurface.toArgb()
    var html by remember { mutableStateOf<String?>(null) }
    var height by remember { mutableStateOf(240.dp) }
    val fontSize by rememberUpdatedState(reading.readerModeFontSize)

    LaunchedEffect(reading.readerModeFont, background, foreground) {
        val theme = AndroidReaderModeResources.theme(context, reading)
        val fallback = ReaderModeScriptProtocol.fontFamily(reading.readerModeFont.storedValue)
        val family = if (theme.fontFaceCss.isBlank()) fallback else "'HarmonicReaderFont', $fallback"
        html = """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
            ${theme.fontFaceCss}
            html,body{margin:0;padding:0;background:${ReaderModeSourceAssembler.cssColor(background)};
                color:${ReaderModeSourceAssembler.cssColor(foreground)};-webkit-text-size-adjust:100%;}
            #preview{padding:20px;overflow-wrap:anywhere;font-family:$family;}
            h1{font:700 var(--reader-title,32px)/1.15 $family;margin:0 0 12px;}
            p{font-size:var(--reader-size,18px);line-height:1.68;margin:0;}
            </style></head><body><main id="preview">
            <h1>Article preview</h1>
            <p>Reader mode brings the words into focus. Adjust the font and text size to find a comfortable way to read your next story.</p>
            </main><script>
            new ResizeObserver(function(){
                PreviewSize.changed(document.getElementById('preview').getBoundingClientRect().height);
            }).observe(document.getElementById('preview'));
            </script></body></html>
        """.trimIndent()
    }
    AndroidView(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth().height(height).clip(RoundedCornerShape(20.dp)),
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
                addJavascriptInterface(PreviewSize { cssPixels ->
                    post { if (tag != null) height = cssPixels.toFloat().coerceIn(1f, 2000f).dp }
                }, "PreviewSize")
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                    override fun onPageFinished(view: WebView, url: String) = view.updateReaderSize(fontSize)
                }
            }
        },
        update = { view ->
            view.setBackgroundColor(background)
            val document = html
            if (document != null && view.tag != document) {
                view.tag = document
                view.loadDataWithBaseURL("https://reader-preview.invalid/", document, "text/html", "UTF-8", null)
            }
            view.updateReaderSize(fontSize)
        },
        onRelease = { view ->
            view.tag = null
            view.removeJavascriptInterface("PreviewSize")
            view.stopLoading()
            view.destroy()
        },
    )
}

private fun WebView.updateReaderSize(size: Int) {
    evaluateJavascript("""
        document.documentElement.style.setProperty('--reader-size', '${size}px');
        document.documentElement.style.setProperty('--reader-title', '${(size * 1.78).roundToInt()}px');
    """.trimIndent(), null)
}

private class PreviewSize(private val onChanged: (Double) -> Unit) {
    @JavascriptInterface
    fun changed(cssPixels: Double) {
        if (cssPixels.isFinite() && cssPixels > 0) onChanged(cssPixels)
    }
}
