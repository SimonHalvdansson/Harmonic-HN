package com.simon.harmonichackernews.desktop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_chevron_left
import com.simon.harmonichackernews.resources.ic_chevron_right
import com.simon.harmonichackernews.resources.ic_forum
import com.simon.harmonichackernews.resources.ic_open_in_new
import com.simon.harmonichackernews.resources.ic_public
import com.simon.harmonichackernews.resources.ic_refresh
import com.simon.harmonichackernews.ui.comments.CommentsComposeController
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

internal data class DesktopBrowserSnapshot(
    val url: String,
    val title: String?,
    val isLoading: Boolean,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
)

internal class DesktopCommentsWebViewSession(
    private val initialUrl: String,
) {
    private var browserHost: SwtEdgeBrowserCanvas? = null

    var currentPageUrl by mutableStateOf(initialUrl)
        private set
    var pageTitle by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var hasLoadedContent by mutableStateOf(false)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set
    var manualInversion by mutableStateOf(false)
        private set

    fun attach(browserHost: SwtEdgeBrowserCanvas) {
        this.browserHost = browserHost
        browserHost.loadUrl(currentPageUrl)
    }

    fun detach(browserHost: SwtEdgeBrowserCanvas) {
        if (this.browserHost === browserHost) this.browserHost = null
    }

    fun updateFromBrowser(
        browserHost: SwtEdgeBrowserCanvas,
        snapshot: DesktopBrowserSnapshot,
    ) {
        if (this.browserHost !== browserHost) return
        val navigatedUrl = snapshot.url.takeUnless(::isTransientBrowserUrl)
        navigatedUrl?.let { currentPageUrl = it }
        snapshot.title
            ?.takeIf { it.isNotBlank() && !isTransientBrowserUrl(it) }
            ?.let { pageTitle = it }
        if (navigatedUrl != null && !snapshot.isLoading) hasLoadedContent = true
        isLoading = snapshot.isLoading || (!hasLoadedContent && navigatedUrl == null)
        canGoBack = snapshot.canGoBack
        canGoForward = snapshot.canGoForward
    }

    fun currentUrl(): String = currentPageUrl

    fun navigateBack() = browserHost?.navigateBack() ?: Unit

    fun navigateForward() = browserHost?.navigateForward() ?: Unit

    fun reload() {
        isLoading = true
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
 * Hosts SWT's native Browser widget in the existing AWT window. On Windows, SWT.EDGE uses the
 * installed Microsoft Edge WebView2 runtime without replacing Harmonic's Compose window host.
 */
@Composable
internal fun DesktopCommentsWebViewScaffold(
    controller: CommentsComposeController,
    initialUrl: String,
    dark: Boolean,
    matchTheme: Boolean,
    nativeSurfaceAllowed: Boolean,
    onSessionChanged: (DesktopCommentsWebViewSession?) -> Unit,
    onOpenExternal: (String) -> Unit,
    comments: @Composable () -> Unit,
) {
    val session = remember(initialUrl) { DesktopCommentsWebViewSession(initialUrl) }
    var showWebsite by remember(controller) { mutableStateOf(controller.initialShowWebsite) }
    var browserStarted by remember(controller) { mutableStateOf(showWebsite) }
    val currentSessionCallback by rememberUpdatedState(onSessionChanged)

    DisposableEffect(session) {
        currentSessionCallback(session)
        onDispose { currentSessionCallback(null) }
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
            session = session,
            onShowWebsite = controller::requestCollapseSheet,
            onShowComments = controller::requestExpandSheet,
            onOpenExternal = { onOpenExternal(session.currentUrl()) },
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (browserStarted) {
                SwtEdgeBrowserSurface(
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
private fun SwtEdgeBrowserSurface(
    session: DesktopCommentsWebViewSession,
    visible: Boolean,
    dark: Boolean,
    matchTheme: Boolean,
) {
    if (!isWindowsDesktop()) {
        DesktopWebViewError("The integrated desktop WebView is currently available on Windows")
        return
    }

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

    val invertPage = (dark && matchTheme) xor session.manualInversion
    LaunchedEffect(session.isLoading, invertPage, browserCanvas) {
        if (!session.isLoading) {
            val filter = if (invertPage) "invert(1) hue-rotate(180deg)" else "none"
            session.evaluateJavaScript(
                "document.documentElement.style.filter='$filter';" +
                    "document.documentElement.style.backgroundColor='transparent';" +
                    MATERIAL_SCROLLBAR_SCRIPT,
            )
        }
    }

    val error = errorMessage
    val nativeSurfaceVisible = visible && error == null && session.hasLoadedContent
    Box(Modifier.fillMaxSize()) {
        SwingPanel(
            factory = { browserCanvas },
            update = { canvas -> canvas.setBrowserVisible(nativeSurfaceVisible) },
            modifier = if (nativeSurfaceVisible) Modifier.fillMaxSize() else Modifier.size(0.dp),
        )
        if (visible && error != null) {
            DesktopWebViewError(error)
        } else if (visible && !session.hasLoadedContent) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    HarmonicLoadingIndicator(modifier = Modifier.size(48.dp))
                    Text(
                        text = "Loading article…",
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopWebViewError(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(24.dp),
            color = Color.DarkGray,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun isWindowsDesktop(): Boolean =
    System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

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
    private val onStateChanged: (SwtEdgeBrowserCanvas, DesktopBrowserSnapshot) -> Unit,
    private val onError: (Throwable) -> Unit,
) : Canvas() {
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

    fun loadUrl(url: String) = withBrowser { it.setUrl(url) }

    fun navigateBack() = withBrowser { if (it.isBackEnabled) it.back() }

    fun navigateForward() = withBrowser { if (it.isForwardEnabled) it.forward() }

    fun reload(fallbackUrl: String) = withBrowser { current ->
        if (isTransientBrowserUrl(current.url)) current.setUrl(fallbackUrl) else current.refresh()
    }

    fun setBrowserVisible(visible: Boolean) {
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

    fun evaluateJavaScript(script: String) = withBrowser { it.execute(script) }

    fun disposeBrowser() {
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
private fun DesktopWebViewToolbar(
    showWebsite: Boolean,
    session: DesktopCommentsWebViewSession,
    onShowWebsite: () -> Unit,
    onShowComments: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    val pageHost = remember(session.currentPageUrl) {
        runCatching { URI(session.currentPageUrl).host }
            .getOrNull()
            ?.removePrefix("www.")
            .orEmpty()
    }
    val openInBrowserInteractions = remember { MutableInteractionSource() }
    val openInBrowserHovered by openInBrowserInteractions.collectIsHoveredAsState()
    Surface(
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = showWebsite,
                    enter = fadeIn(tween(160)) + expandHorizontally(
                        animationSpec = tween(180),
                        expandFrom = Alignment.Start,
                    ),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(
                        animationSpec = tween(180),
                        shrinkTowards = Alignment.Start,
                    ),
                    label = "web navigation controls",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        DesktopWebViewIconButton(
                            icon = Res.drawable.ic_chevron_left,
                            description = "Back",
                            onClick = session::navigateBack,
                            enabled = session.canGoBack,
                        )
                        DesktopWebViewIconButton(
                            icon = Res.drawable.ic_chevron_right,
                            description = "Forward",
                            onClick = session::navigateForward,
                            enabled = session.canGoForward,
                        )
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            AnimatedContent(
                                targetState = session.isLoading,
                                transitionSpec = {
                                    fadeIn(tween(140)) togetherWith fadeOut(tween(100))
                                },
                                label = "web reload loading transition",
                            ) { loading ->
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
                        Spacer(Modifier.width(8.dp))
                    }
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        text = session.pageTitle ?: session.currentPageUrl,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
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

                AnimatedVisibility(
                    visible = showWebsite,
                    enter = fadeIn(tween(160)) + expandHorizontally(
                        animationSpec = tween(180),
                        expandFrom = Alignment.End,
                    ),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(
                        animationSpec = tween(180),
                        shrinkTowards = Alignment.End,
                    ),
                    label = "open in browser button",
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(4.dp))
                        AnimatedVisibility(
                            visible = openInBrowserHovered,
                            enter = fadeIn(tween(120)) + expandHorizontally(
                                animationSpec = tween(150),
                                expandFrom = Alignment.End,
                            ),
                            exit = fadeOut(tween(90)) + shrinkHorizontally(
                                animationSpec = tween(120),
                                shrinkTowards = Alignment.End,
                            ),
                            label = "open in browser tooltip",
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.inverseSurface,
                                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                    shadowElevation = 2.dp,
                                ) {
                                    Text(
                                        text = "Open in browser",
                                        modifier = Modifier.padding(
                                            horizontal = 10.dp,
                                            vertical = 6.dp,
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                        }
                        FilledTonalIconButton(
                            onClick = onOpenExternal,
                            modifier = Modifier.hoverable(openInBrowserInteractions),
                        ) {
                            Icon(
                                painterResource(Res.drawable.ic_open_in_new),
                                contentDescription = "Open in browser",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = showWebsite,
                        onClick = onShowWebsite,
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            Icon(
                                painterResource(Res.drawable.ic_public),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        label = { Text("Article") },
                    )
                    SegmentedButton(
                        selected = !showWebsite,
                        onClick = onShowComments,
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                painterResource(Res.drawable.ic_forum),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        label = { Text("Comments") },
                    )
                }
            }
        }
    }
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
