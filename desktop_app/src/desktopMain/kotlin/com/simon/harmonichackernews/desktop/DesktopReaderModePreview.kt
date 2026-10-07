package com.simon.harmonichackernews.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.settings.ReadingPreferences
import com.simon.harmonichackernews.ui.reader.ReaderModeResources
import com.simon.harmonichackernews.ui.reader.ReaderPreviewDocument
import com.simon.harmonichackernews.ui.theme.cardBackground
import java.awt.Canvas

@Composable
internal fun DesktopReaderModePreview(reading: ReadingPreferences, visible: Boolean) {
    val colors = MaterialTheme.colorScheme
    val background = colors.cardBackground.toArgb()
    val foreground = colors.onSurface.toArgb()
    var height by remember { mutableStateOf(240.dp) }
    var error by remember { mutableStateOf(false) }
    val sizeScript by rememberUpdatedState(ReaderPreviewDocument.sizeScript(
        reading.readerModeFontSize, reading.readerModeLineHeight.multiplier,
    ))
    val canvas: Canvas? = remember {
        val onState: (DesktopBrowserHost, DesktopBrowserSnapshot) -> Unit = { host, snapshot ->
            // This local document publishes ResizeObserver measurements in its title. Both native
            // browser adapters already report title changes, including late web-font loading.
            snapshot.title?.removePrefix("HarmonicPreview:")?.toFloatOrNull()
                ?.takeIf { it.isFinite() && it > 0 }?.let { height = it.coerceIn(1f, 2000f).dp }
            if (!snapshot.isLoading) host.evaluateJavaScript(sizeScript)
        }
        when (desktopEmbeddedBrowserBackend()) {
            DesktopEmbeddedBrowserBackend.WINDOWS_EDGE -> SwtEdgeBrowserCanvas(onState, { error = true })
            DesktopEmbeddedBrowserBackend.MAC_WEBKIT -> MacWkWebViewCanvas(onState, { error = true })
            DesktopEmbeddedBrowserBackend.UNSUPPORTED -> null
        }
    }
    val browser = canvas as? DesktopBrowserHost
    DisposableEffect(browser) { onDispose { browser?.disposeBrowser() } }
    LaunchedEffect(reading.readerModeFont, background, foreground, browser) {
        val font = reading.readerModeFont.storedValue
        val html = ReaderPreviewDocument.html(font, ReaderModeResources.fontData(font)?.fontFaceCss.orEmpty(),
            background, foreground, "document.title='HarmonicPreview:'+height;")
        browser?.loadHtml(html)
    }
    LaunchedEffect(sizeScript, browser) { browser?.evaluateJavaScript(sizeScript) }
    if (canvas != null && !error) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(height)) {
            SwingPanel(
                factory = { canvas },
                update = { browser?.setBrowserVisible(visible) },
                modifier = if (visible) Modifier.fillMaxSize() else Modifier.size(0.dp),
                background = colors.cardBackground,
            )
        }
    } else {
        Text("Reader preview is unavailable because the embedded browser could not be started.",
            Modifier.padding(20.dp))
    }
}
