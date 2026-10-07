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
import com.simon.harmonichackernews.settings.ReadingPreferences
import com.simon.harmonichackernews.ui.theme.cardBackground

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
    val currentReading by rememberUpdatedState(reading)

    LaunchedEffect(reading.readerModeFont, background, foreground) {
        val theme = AndroidReaderModeResources.theme(context, reading)
        html = com.simon.harmonichackernews.ui.reader.ReaderPreviewDocument.html(
            reading.readerModeFont.storedValue, theme.fontFaceCss, background, foreground,
            "PreviewSize.changed(height);",
        )
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
                    override fun onPageFinished(view: WebView, url: String) = view.updateReaderSize(currentReading)
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
            view.updateReaderSize(currentReading)
        },
        onRelease = { view ->
            view.tag = null
            view.removeJavascriptInterface("PreviewSize")
            view.stopLoading()
            view.destroy()
        },
    )
}

private fun WebView.updateReaderSize(reading: ReadingPreferences) {
    evaluateJavascript(com.simon.harmonichackernews.ui.reader.ReaderPreviewDocument.sizeScript(
        reading.readerModeFontSize, reading.readerModeLineHeight.multiplier,
    ), null)
}

private class PreviewSize(private val onChanged: (Double) -> Unit) {
    @JavascriptInterface
    fun changed(cssPixels: Double) {
        if (cssPixels.isFinite() && cssPixels > 0) onChanged(cssPixels)
    }
}
