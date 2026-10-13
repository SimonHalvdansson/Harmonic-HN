package com.simon.harmonichackernews.desktop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_chevron_left
import com.simon.harmonichackernews.resources.ic_chevron_right
import com.simon.harmonichackernews.resources.ic_forum
import com.simon.harmonichackernews.resources.ic_open_in_new
import com.simon.harmonichackernews.resources.ic_public
import com.simon.harmonichackernews.resources.ic_refresh
import com.simon.harmonichackernews.ui.comments.CommentsScreenController
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import java.awt.Canvas
import java.awt.EventQueue
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.eclipse.swt.SWT
import org.eclipse.swt.awt.SWT_AWT
import org.eclipse.swt.browser.Browser
import org.eclipse.swt.browser.LocationAdapter
import org.eclipse.swt.browser.LocationEvent
import org.eclipse.swt.browser.ProgressAdapter
import org.eclipse.swt.browser.ProgressEvent
import org.eclipse.swt.browser.TitleEvent
import org.eclipse.swt.browser.TitleListener
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.Display
import org.eclipse.swt.widgets.Shell
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.simon.harmonichackernews.presentation.NativeReaderModeSession
import com.simon.harmonichackernews.resources.ic_book_ribbon
import com.simon.harmonichackernews.settings.ReadingPreferences
import com.simon.harmonichackernews.ui.reader.ReaderModeResources
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive

internal data class DesktopBrowserSnapshot(
    val url: String,
    val title: String?,
    val isLoading: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val loadError: String? = null,
)

internal interface DesktopBrowserHost {
    fun loadUrl(url: String)
    fun loadHtml(html: String)
    fun evaluateJavaScriptResult(script: String, result: (String?) -> Unit)
    fun navigateBack()
    fun navigateForward()
    fun reload(fallbackUrl: String)
    fun setBrowserVisible(visible: Boolean)
    fun evaluateJavaScript(script: String)
    fun disposeBrowser()
}

internal class DesktopCommentsWebViewSession(
    initialUrl: String,
) {
    private var browserHost: DesktopBrowserHost? = null
    var reader: NativeReaderModeSession? = null
    private var documentGeneration = 0

    suspend fun evaluateResult(script: String): String? {
        val host = browserHost ?: return null
        val expectedGeneration = documentGeneration
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { continuation ->
                host.evaluateJavaScriptResult(script) { result ->
                    if (continuation.isActive) continuation.resumeWith(Result.success(
                        result.takeIf { browserHost === host && documentGeneration == expectedGeneration },
                    ))
                }
            }
        }
    }

    var currentPageUrl by mutableStateOf(initialUrl)
        private set
    var pageTitle by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var hasLoadedContent by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set
    var manualInversion by mutableStateOf(false)
        private set

    fun attach(browserHost: DesktopBrowserHost) {
        this.browserHost = browserHost
        reader?.navigationStarted(currentPageUrl)
        browserHost.loadUrl(currentPageUrl)
    }

    fun detach(browserHost: DesktopBrowserHost) {
        if (this.browserHost === browserHost) {
            this.browserHost = null
            documentGeneration++
            reader?.navigationStarted(null)
        }
    }

    fun updateFromBrowser(
        browserHost: DesktopBrowserHost,
        snapshot: DesktopBrowserSnapshot,
    ) {
        if (this.browserHost !== browserHost) return
        val navigatedUrl = snapshot.url.takeUnless(::isTransientBrowserUrl)
        if (snapshot.loadError != null) {
            if (loadError != snapshot.loadError || currentPageUrl != navigatedUrl) {
                documentGeneration++
                reader?.navigationStarted(null)
            }
            navigatedUrl?.let { currentPageUrl = it }
            loadError = snapshot.loadError
            isLoading = false
            canGoBack = snapshot.canGoBack
            canGoForward = snapshot.canGoForward
            return
        }
        loadError = null
        val started = (snapshot.isLoading && !isLoading) ||
            (navigatedUrl != null && navigatedUrl != currentPageUrl)
        val finished = !snapshot.isLoading && (isLoading || started)
        if (started) {
            documentGeneration++
            reader?.navigationStarted(navigatedUrl)
        }
        navigatedUrl?.let { currentPageUrl = it }
        snapshot.title
            ?.takeIf { it.isNotBlank() && !isTransientBrowserUrl(it) }
            ?.let { pageTitle = it }
        if (navigatedUrl != null && !snapshot.isLoading) hasLoadedContent = true
        isLoading = snapshot.isLoading || (!hasLoadedContent && navigatedUrl == null)
        canGoBack = snapshot.canGoBack
        canGoForward = snapshot.canGoForward
        if (finished && navigatedUrl != null) reader?.pageFinished(navigatedUrl)
    }

    fun currentUrl(): String = currentPageUrl

    fun navigateBack() = browserHost?.navigateBack() ?: Unit

    fun navigateForward() = browserHost?.navigateForward() ?: Unit

    fun reload() {
        documentGeneration++
        reader?.navigationStarted(currentPageUrl)
        isLoading = true
        loadError = null
        browserHost?.reload(currentPageUrl)
    }

    fun evaluateJavaScript(script: String) {
        browserHost?.evaluateJavaScript(script)
    }

    fun toggleInversion() {
        manualInversion = !manualInversion
    }
}

/**
 * Hosts a platform browser in the existing Compose/AWT window: Edge WebView2 through SWT on
 * Windows and an AppKit WKWebView child surface on macOS.
 */
@Composable
internal fun DesktopCommentsWebViewScaffold(
    controller: CommentsScreenController,
    initialUrl: String,
    dark: Boolean,
    matchTheme: Boolean,
    nativeSurfaceAllowed: Boolean,
    onSessionChanged: (DesktopCommentsWebViewSession?) -> Unit,
    reading: ReadingPreferences,
    onReaderMessage: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    comments: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val readerMessage by rememberUpdatedState(onReaderMessage)
    val session = remember(initialUrl) {
        DesktopCommentsWebViewSession(initialUrl).also { browser ->
            browser.reader = NativeReaderModeSession(
                scope, browser::evaluateResult, script = ReaderModeResources::script, onMessage = { readerMessage(it) },
            )
        }
    }
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(session, reading, colors, dark) {
        session.reader?.configure(reading) {
            ReaderModeResources.theme(colors, !dark, reading)
        }
    }
    var showWebsite by remember(controller) { mutableStateOf(controller.initialShowWebsite) }
    var browserStarted by remember(controller) { mutableStateOf(showWebsite) }
    val currentSessionCallback by rememberUpdatedState(onSessionChanged)

    DisposableEffect(session) {
        currentSessionCallback(session)
        onDispose { session.reader?.dispose(); currentSessionCallback(null) }
    }

    LaunchedEffect(controller) {
        if (showWebsite) browserStarted = true
        val expandedFraction = if (showWebsite) 0f else 1f
        controller.updateSheet(expandedFraction, controller.topInsetPx)
        controller.listener.onSheetProgressChanged(expandedFraction)
        controller.listener.onSheetSettled(!showWebsite)
    }

    val sheetRequest = controller.sheetRequest
    LaunchedEffect(sheetRequest) {
        val request = sheetRequest ?: return@LaunchedEffect
        showWebsite = !request.expanded
        if (showWebsite) browserStarted = true
        val expandedFraction = if (request.expanded) 1f else 0f
        controller.updateSheet(expandedFraction, controller.topInsetPx)
        controller.listener.onSheetProgressChanged(expandedFraction)
        controller.listener.onSheetSettled(request.expanded)
        controller.consumeSheetRequest(request)
    }

    Column(Modifier.fillMaxSize()) {
        DesktopWebViewToolbar(
            showWebsite = showWebsite,
            storyTitle = controller.story.title,
            session = session,
            onShowWebsite = controller::requestCollapseSheet,
            onShowComments = controller::requestExpandSheet,
            onOpenExternal = { onOpenExternal(session.currentUrl()) },
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.pageBackground),
        ) {
            if (browserStarted) {
                DesktopEmbeddedBrowserSurface(
                    session = session,
                    visible = showWebsite && nativeSurfaceAllowed,
                    dark = dark,
                    matchTheme = matchTheme,
                )
            }
            Crossfade(
                targetState = !showWebsite,
                animationSpec = tween(140),
                modifier = Modifier.fillMaxSize(),
                label = "article comments transition",
            ) { commentsVisible ->
                if (commentsVisible) comments()
            }
        }
    }
}

@Composable
private fun DesktopEmbeddedBrowserSurface(
    session: DesktopCommentsWebViewSession,
    visible: Boolean,
    dark: Boolean,
    matchTheme: Boolean,
) {
    when (desktopEmbeddedBrowserBackend()) {
        DesktopEmbeddedBrowserBackend.WINDOWS_EDGE -> SwtEdgeBrowserSurface(
            session = session,
            visible = visible,
            dark = dark,
            matchTheme = matchTheme,
        )
        DesktopEmbeddedBrowserBackend.MAC_WEBKIT -> MacWkWebViewSurface(
            session = session,
            visible = visible,
            dark = dark,
            matchTheme = matchTheme,
        )
        DesktopEmbeddedBrowserBackend.UNSUPPORTED -> DesktopWebViewError(
            "The integrated desktop WebView is not available on this platform",
        )
    }
}

@Composable
private fun SwtEdgeBrowserSurface(
    session: DesktopCommentsWebViewSession,
    visible: Boolean,
    dark: Boolean,
    matchTheme: Boolean,
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val browserCanvas = remember(session) {
        SwtEdgeBrowserCanvas(
            onStateChanged = session::updateFromBrowser,
            onError = { error ->
                errorMessage = error.message ?: "Microsoft Edge WebView2 could not be started"
            },
        )
    }

    DisposableEffect(browserCanvas, session) {
        session.attach(browserCanvas)
        onDispose {
            session.detach(browserCanvas)
            browserCanvas.disposeBrowser()
        }
    }

    LaunchedEffect(session.isLoading, dark, matchTheme, session.manualInversion, browserCanvas) {
        if (!session.isLoading) {
            session.evaluateJavaScript(
                desktopWebViewAppearanceScript(dark, matchTheme, session.manualInversion) +
                    MATERIAL_SCROLLBAR_SCRIPT,
            )
        }
    }

    val error = errorMessage ?: session.loadError
    val nativeSurfaceVisible = visible && error == null
    Box(Modifier.fillMaxSize()) {
        SwingPanel(
            factory = { browserCanvas },
            update = { canvas -> canvas.setBrowserVisible(nativeSurfaceVisible) },
            modifier = if (nativeSurfaceVisible) Modifier.fillMaxSize() else Modifier.size(0.dp),
        )
        if (visible && error != null) {
            DesktopWebViewError(error, onRetry = if (errorMessage == null) session::reload else null)
        }
    }
}

@Composable
private fun MacWkWebViewSurface(
    session: DesktopCommentsWebViewSession,
    visible: Boolean,
    dark: Boolean,
    matchTheme: Boolean,
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val browserCanvas = remember(session) {
        MacWkWebViewCanvas(
            onStateChanged = session::updateFromBrowser,
            onError = { error ->
                errorMessage = error.message ?: "The macOS WebView could not be started"
            },
        )
    }

    DisposableEffect(browserCanvas, session) {
        session.attach(browserCanvas)
        onDispose {
            session.detach(browserCanvas)
            browserCanvas.disposeBrowser()
        }
    }

    LaunchedEffect(session.isLoading, dark, matchTheme, session.manualInversion, browserCanvas) {
        if (!session.isLoading) {
            session.evaluateJavaScript(
                desktopWebViewAppearanceScript(dark, matchTheme, session.manualInversion),
            )
        }
    }

    val error = errorMessage ?: session.loadError
    val browserVisible = visible && error == null
    Box(Modifier.fillMaxSize()) {
        SwingPanel(
            factory = { browserCanvas },
            update = { canvas -> canvas.setBrowserVisible(browserVisible) },
            modifier = if (browserVisible) Modifier.fillMaxSize() else Modifier.size(0.dp),
        )
        if (visible && error != null) {
            DesktopWebViewError(error, onRetry = if (errorMessage == null) session::reload else null)
        }
    }
}

@Composable
private fun DesktopWebViewError(message: String, onRetry: (() -> Unit)? = null) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.pageBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (onRetry != null) androidx.compose.material3.TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

internal enum class DesktopEmbeddedBrowserBackend {
    WINDOWS_EDGE,
    MAC_WEBKIT,
    UNSUPPORTED,
}

internal fun desktopEmbeddedBrowserBackend(
    osName: String = System.getProperty("os.name").orEmpty(),
): DesktopEmbeddedBrowserBackend = when {
    osName.startsWith("Windows", ignoreCase = true) ->
        DesktopEmbeddedBrowserBackend.WINDOWS_EDGE
    osName.startsWith("Mac", ignoreCase = true) ->
        DesktopEmbeddedBrowserBackend.MAC_WEBKIT
    else -> DesktopEmbeddedBrowserBackend.UNSUPPORTED
}

private fun isTransientBrowserUrl(url: String): Boolean =
    url.isBlank() || url.equals("about:blank", ignoreCase = true)

private const val MATERIAL_SCROLLBAR_SCRIPT =
    "(function(){" +
        "var id='harmonic-material-scrollbars';" +
        "var style=document.getElementById(id);" +
        "if(!style){style=document.createElement('style');style.id=id;" +
        "(document.head||document.documentElement).appendChild(style);}" +
        "style.textContent=" +
        "'*{scrollbar-width:thin;scrollbar-color:rgba(127,127,127,.48) transparent}' +" +
        "'::-webkit-scrollbar{width:12px;height:12px}' +" +
        "'::-webkit-scrollbar-track,::-webkit-scrollbar-corner{background:transparent}' +" +
        "'::-webkit-scrollbar-thumb{background:rgba(127,127,127,.48);" +
        "border:3px solid transparent;border-radius:999px;background-clip:padding-box;" +
        "min-height:40px}' +" +
        "'::-webkit-scrollbar-thumb:hover{background:rgba(127,127,127,.72);" +
        "border:3px solid transparent;background-clip:padding-box}';" +
        "})();"

private object DesktopSwtDisplayHost {
    private val started = AtomicBoolean(false)
    private val stateLock = Any()
    private val pendingActions = mutableListOf<(Display) -> Unit>()

    @Volatile
    private var display: Display? = null

    fun async(action: (Display) -> Unit) {
        val currentDisplay = synchronized(stateLock) {
            display?.takeUnless(Display::isDisposed).also { activeDisplay ->
                if (activeDisplay == null) pendingActions += action
            }
        }
        if (currentDisplay != null) {
            runCatching { currentDisplay.asyncExec { action(currentDisplay) } }
            return
        }
        if (!started.compareAndSet(false, true)) return

        Thread(
            {
                val localDisplay = Display()
                val queuedActions = synchronized(stateLock) {
                    display = localDisplay
                    pendingActions.toList().also { pendingActions.clear() }
                }
                queuedActions.forEach { action -> runCatching { action(localDisplay) } }
                while (!localDisplay.isDisposed) {
                    if (!localDisplay.readAndDispatch()) localDisplay.sleep()
                }
            },
            "Harmonic-WebView2",
        ).apply {
            isDaemon = true
            start()
        }
    }
}

internal class SwtEdgeBrowserCanvas(
    private val onStateChanged: (DesktopBrowserHost, DesktopBrowserSnapshot) -> Unit,
    private val onError: (Throwable) -> Unit,
) : Canvas(), DesktopBrowserHost {
    private val started = AtomicBoolean(false)
    private val disposed = AtomicBoolean(false)
    private val stateLock = Any()
    private val pendingActions = mutableListOf<(Browser) -> Unit>()

    @Volatile
    private var display: Display? = null

    @Volatile
    private var shell: Shell? = null

    @Volatile
    private var browser: Browser? = null

    @Volatile
    private var browserVisible: Boolean = true

    init {
        background = java.awt.Color.WHITE
        addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent) {
                    syncEmbeddedShellBounds()
                }

                override fun componentMoved(event: ComponentEvent) {
                    syncEmbeddedShellBounds()
                }
            },
        )
    }

    override fun addNotify() {
        super.addNotify()
        if (started.compareAndSet(false, true)) startSwtEventLoop()
    }

    override fun removeNotify() {
        disposeBrowser()
        super.removeNotify()
    }

    override fun loadUrl(url: String) = withBrowser { it.setUrl(url) }

    override fun loadHtml(html: String) = withBrowser { it.setText(html, true) }

    override fun evaluateJavaScriptResult(script: String, result: (String?) -> Unit) = withBrowser {
        val value = runCatching { it.evaluate("return eval(" + JsonPrimitive(script).toString() + ");")?.toString() }.getOrNull()
        EventQueue.invokeLater { if (!disposed.get()) result(value) }
    }

    override fun navigateBack() = withBrowser { if (it.isBackEnabled) it.back() }

    override fun navigateForward() = withBrowser { if (it.isForwardEnabled) it.forward() }

    override fun reload(fallbackUrl: String) = withBrowser { current ->
        if (isTransientBrowserUrl(current.url)) current.setUrl(fallbackUrl) else current.refresh()
    }

    override fun setBrowserVisible(visible: Boolean) {
        browserVisible = visible
        isVisible = visible
        val targetDisplay = display ?: return
        val targetShell = shell ?: return
        runCatching {
            targetDisplay.asyncExec {
                if (!targetShell.isDisposed) targetShell.isVisible = visible
            }
        }
    }

    override fun evaluateJavaScript(script: String) = withBrowser { it.execute(script) }

    override fun disposeBrowser() {
        if (!disposed.compareAndSet(false, true)) return
        synchronized(stateLock) { pendingActions.clear() }
        val currentDisplay = display ?: return
        val currentShell = shell
        browser = null
        shell = null
        display = null
        runCatching {
            currentDisplay.asyncExec {
                currentShell?.takeUnless(Shell::isDisposed)?.dispose()
            }
        }
    }

    private fun startSwtEventLoop() {
        DesktopSwtDisplayHost.async { localDisplay ->
            try {
                System.setProperty(
                    "org.eclipse.swt.browser.EdgeAllowSingleSignOnUsingOSPrimaryAccount",
                    "false",
                )
                if (disposed.get()) return@async
                display = localDisplay

                val localShell = SWT_AWT.new_Shell(localDisplay, this)
                shell = localShell
                localShell.layout = FillLayout()
                localShell.setSize(width.coerceAtLeast(1), height.coerceAtLeast(1))

                val localBrowser = Browser(localShell, SWT.EDGE)
                installListeners(localBrowser)
                val queuedActions = synchronized(stateLock) {
                    browser = localBrowser
                    pendingActions.toList().also { pendingActions.clear() }
                }
                localShell.open()
                localShell.isVisible = browserVisible
                localShell.layout(true, true)
                EventQueue.invokeLater(::syncEmbeddedShellBounds)

                val edgeVersion = System.getProperty("org.eclipse.swt.browser.EdgeVersion")
                if (edgeVersion.isNullOrBlank()) {
                    error("Microsoft Edge WebView2 Runtime is not available")
                }
                println("Harmonic desktop WebView: Microsoft Edge WebView2 $edgeVersion")
                queuedActions.forEach { action -> action(localBrowser) }
            } catch (error: Throwable) {
                EventQueue.invokeLater { onError(error) }
            }
        }
    }

    private fun installListeners(localBrowser: Browser) {
        var loading = false
        var title: String? = null

        localBrowser.addLocationListener(
            object : LocationAdapter() {
                override fun changing(event: LocationEvent) {
                    loading = true
                    publishSnapshot(localBrowser, loading, title)
                }

                override fun changed(event: LocationEvent) {
                    publishSnapshot(localBrowser, loading, title)
                }
            },
        )
        localBrowser.addProgressListener(
            object : ProgressAdapter() {
                override fun changed(event: ProgressEvent) {
                    loading = true
                    publishSnapshot(localBrowser, loading, title)
                }

                override fun completed(event: ProgressEvent) {
                    loading = false
                    publishSnapshot(localBrowser, loading, title)
                }
            },
        )
        localBrowser.addTitleListener(
            TitleListener { event: TitleEvent ->
                title = event.title
                publishSnapshot(localBrowser, loading, title)
            },
        )
    }

    private fun syncEmbeddedShellBounds() {
        val targetDisplay = display ?: return
        val targetShell = shell ?: return
        val targetWidth = width.coerceAtLeast(1)
        val targetHeight = height.coerceAtLeast(1)
        runCatching {
            targetDisplay.asyncExec {
                if (!targetShell.isDisposed) {
                    targetShell.setBounds(
                        0,
                        0,
                        targetWidth,
                        targetHeight,
                    )
                    targetShell.layout(true, true)
                }
            }
        }
    }

    private fun publishSnapshot(
        localBrowser: Browser,
        isLoading: Boolean,
        title: String? = null,
    ) {
        if (localBrowser.isDisposed) return
        val snapshot = DesktopBrowserSnapshot(
            url = localBrowser.url,
            title = title,
            isLoading = isLoading,
            canGoBack = localBrowser.isBackEnabled,
            canGoForward = localBrowser.isForwardEnabled,
        )
        EventQueue.invokeLater { onStateChanged(this, snapshot) }
    }

    private fun withBrowser(action: (Browser) -> Unit) {
        val target = synchronized(stateLock) {
            if (disposed.get()) return
            val currentBrowser = browser
            val currentDisplay = display
            if (currentBrowser == null || currentDisplay == null) {
                pendingActions += action
                return
            }
            currentDisplay to currentBrowser
        }
        runCatching {
            target.first.asyncExec {
                if (!disposed.get() && !target.second.isDisposed) action(target.second)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopWebViewToolbar(
    showWebsite: Boolean,
    storyTitle: String?,
    session: DesktopCommentsWebViewSession,
    onShowWebsite: () -> Unit,
    onShowComments: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val readerState = session.reader?.state?.collectAsState()?.value
    val pageHost = remember(session.currentPageUrl) {
        runCatching { URI(session.currentPageUrl).host }
            .getOrNull()
            ?.removePrefix("www.")
            .orEmpty()
    }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val tabWidths = listOf("Article", "Comments").map {
            with(density) {
                measurer.measure(it, MaterialTheme.typography.labelLarge, maxLines = 1).size.width.toDp()
            } + 56.dp
        }
        // Reserve the article controls for this decision so switching tabs doesn't
        // also change the label mode. The title yields before any control wraps.
        val labeledTabsWidth = tabWidths.reduce { total, width -> total + width }
        val tabLabels = maxWidth >= 224.dp + labeledTabsWidth
        val tabsWidth = if (tabLabels) labeledTabsWidth else 96.dp
        val actionsWidth = if (showWebsite) {
            156.dp + if (readerState?.available == true) 36.dp else 0.dp
        } else 0.dp
        val showTitle = maxWidth - 32.dp - actionsWidth - tabsWidth >= 160.dp * density.fontScale
        Surface(color = MaterialTheme.colorScheme.pageBackground) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarActionVisibility(showWebsite) {
                    DesktopWebNavigation(session, onShowComments)
                }
                Box(Modifier.weight(1f).padding(end = 6.dp)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showTitle,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(100)),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Crossfade(
                                targetState = desktopWebViewToolbarTitle(
                                    showWebsite = showWebsite,
                                    storyTitle = storyTitle,
                                    pageTitle = session.pageTitle,
                                    currentPageUrl = session.currentPageUrl,
                                ),
                                animationSpec = tween(140),
                                label = "web toolbar title",
                            ) { title ->
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (pageHost.isNotEmpty()) {
                                Text(
                                    text = pageHost,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                ToolbarActionVisibility(showWebsite && readerState?.available == true) {
                    DesktopWebViewIconButton(
                        icon = Res.drawable.ic_book_ribbon,
                        description = if (readerState?.enabled == true) "Exit reader mode" else "Reader mode",
                        onClick = { session.reader?.toggle() },
                    )
                }
                ToolbarActionVisibility(showWebsite) {
                    FilledTonalIconButton(
                        onClick = onOpenExternal,
                    ) {
                        Icon(
                            painterResource(Res.drawable.ic_open_in_new),
                            contentDescription = "Open in browser",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                DesktopWebTabs(showWebsite, onShowWebsite, onShowComments, tabLabels, tabWidths)
            }
        }
    }
}

@Composable
private fun ToolbarActionVisibility(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(tween(200), expandFrom = Alignment.Start) + fadeIn(tween(150)),
        exit = shrinkHorizontally(tween(200), shrinkTowards = Alignment.Start) + fadeOut(tween(100)),
    ) { content() }
}

@Composable
private fun DesktopWebNavigation(session: DesktopCommentsWebViewSession, onShowComments: () -> Unit) {
    Row {
        DesktopWebViewIconButton(
            icon = Res.drawable.ic_chevron_left,
            description = "Back",
            onClick = {
                if (session.canGoBack) session.navigateBack() else onShowComments()
            },
        )
        DesktopWebViewIconButton(
            icon = Res.drawable.ic_chevron_right,
            description = "Forward",
            onClick = session::navigateForward,
            enabled = session.canGoForward,
        )
        Crossfade(
            targetState = session.isLoading,
            modifier = Modifier.size(36.dp),
            animationSpec = tween(140),
            label = "web reload loading transition",
        ) { loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (loading) {
                    HarmonicLoadingIndicator(modifier = Modifier.size(24.dp))
                } else {
                    DesktopWebViewIconButton(
                        icon = Res.drawable.ic_refresh,
                        description = "Reload",
                        onClick = session::reload,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopWebTabs(
    showWebsite: Boolean,
    onShowWebsite: () -> Unit,
    onShowComments: () -> Unit,
    showLabels: Boolean,
    labelWidths: List<Dp>,
) {
    SingleChoiceSegmentedButtonRow {
        listOf("Article" to Res.drawable.ic_public, "Comments" to Res.drawable.ic_forum)
            .forEachIndexed { index, (label, icon) ->
                val width by animateDpAsState(
                    targetValue = if (showLabels) labelWidths[index] else 48.dp,
                    animationSpec = tween(200),
                    label = "web tab width",
                )
                SegmentedButton(
                    selected = showWebsite == (index == 0),
                    modifier = Modifier.width(width).semantics { contentDescription = label },
                    onClick = if (index == 0) onShowWebsite else onShowComments,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    icon = {},
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(icon), null, Modifier.size(18.dp))
                            ToolbarActionVisibility(showLabels) {
                                Text(label, Modifier.padding(start = 8.dp), maxLines = 1, softWrap = false)
                            }
                        }
                    },
                )
            }
    }
}

internal fun desktopWebViewToolbarTitle(
    showWebsite: Boolean,
    storyTitle: String?,
    pageTitle: String?,
    currentPageUrl: String,
): String {
    val preferredTitle = if (showWebsite) pageTitle else storyTitle
    return preferredTitle?.takeIf(String::isNotBlank) ?: currentPageUrl
}

@Composable
private fun DesktopWebViewIconButton(
    icon: DrawableResource,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val tint by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
        },
        animationSpec = tween(120),
        label = "$description enabled state",
    )
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
    }
}
