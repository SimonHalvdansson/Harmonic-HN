package com.simon.harmonichackernews.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.Canvas
import java.awt.EventQueue
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import javax.swing.Timer

/** A heavyweight AWT slot whose native NSView owns an actual AppKit WKWebView child. */
internal class MacWkWebViewCanvas(
    private val onStateChanged: (DesktopBrowserHost, DesktopBrowserSnapshot) -> Unit,
    private val onError: (Throwable) -> Unit,
) : Canvas(), DesktopBrowserHost {
    private val started = AtomicBoolean(false)
    private val disposed = AtomicBoolean(false)
    private val pollingStarted = AtomicBoolean(false)
    private val stateLock = Any()
    private val nativeCallLock = Any()
    private val pendingActions = mutableListOf<(MacWebViewApi, Pointer) -> Unit>()
    private val poller = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "Harmonic-WKWebView-State").apply { isDaemon = true }
    }

    @Volatile
    private var api: MacWebViewApi? = null

    @Volatile
    private var host: Pointer? = null

    @Volatile
    private var browserVisible = false

    @Volatile
    private var lastSnapshot: DesktopBrowserSnapshot? = null

    @Volatile
    private var resizeInProgress = false

    private val resizeSettleTimer = Timer(RESIZE_SETTLE_DELAY_MS) {
        resizeInProgress = false
        resizeBrowserNow()
    }.apply { isRepeats = false }

    init {
        background = java.awt.Color.WHITE
        addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent?) = scheduleBrowserResize()
                override fun componentMoved(event: ComponentEvent?) = scheduleBrowserResize()
            },
        )
    }

    override fun addNotify() {
        super.addNotify()
        if (!started.compareAndSet(false, true)) return
        if (EventQueue.isDispatchThread()) {
            initializeBrowser()
        } else {
            EventQueue.invokeLater(::initializeBrowser)
        }
    }

    override fun removeNotify() {
        disposeBrowser()
        super.removeNotify()
    }

    override fun loadUrl(url: String) = withHost { native, pointer ->
        if (url.isNotBlank()) native.harmonic_webview_load_url(pointer, url)
    }

    override fun navigateBack() = withHost { native, pointer ->
        native.harmonic_webview_go_back(pointer)
    }

    override fun navigateForward() = withHost { native, pointer ->
        native.harmonic_webview_go_forward(pointer)
    }

    override fun reload(fallbackUrl: String) = withHost { native, pointer ->
        native.harmonic_webview_reload(pointer, fallbackUrl)
    }

    override fun setBrowserVisible(visible: Boolean) {
        browserVisible = visible
        val updateAwtVisibility = {
            if (!disposed.get()) isVisible = visible
        }
        if (EventQueue.isDispatchThread()) {
            updateAwtVisibility()
        } else {
            EventQueue.invokeLater(updateAwtVisibility)
        }
        withHost { native, pointer ->
            native.harmonic_webview_set_visible(pointer, if (visible) 1 else 0)
        }
    }

    override fun evaluateJavaScript(script: String) = withHost { native, pointer ->
        native.harmonic_webview_evaluate_javascript(pointer, script)
    }

    override fun disposeBrowser() {
        if (!disposed.compareAndSet(false, true)) return
        if (EventQueue.isDispatchThread()) {
            resizeSettleTimer.stop()
        } else {
            EventQueue.invokeLater(resizeSettleTimer::stop)
        }
        poller.shutdownNow()
        val target = synchronized(stateLock) {
            pendingActions.clear()
            val current = api?.let { native -> host?.let { pointer -> native to pointer } }
            api = null
            host = null
            current
        }
        if (target != null) {
            runCatching {
                synchronized(nativeCallLock) {
                    target.first.harmonic_webview_destroy(target.second)
                }
            }.onFailure(::reportError)
        }
    }

    private fun initializeBrowser() {
        try {
            if (disposed.get()) return
            check(System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
                "WKWebView is only available on macOS"
            }
            val native = MacWebViewLibrary.api
            val bounds = browserBounds() ?: error("The macOS window is not ready for its WebView")
            check(Pointer.nativeValue(bounds.parentView) != 0L) {
                "AWT did not provide its native macOS content view"
            }
            val pointer = native.harmonic_webview_create(
                bounds.parentView,
                bounds.x,
                bounds.top,
                bounds.width,
                bounds.height,
            ) ?: error("macOS did not create the native WKWebView")
            if (disposed.get()) {
                native.harmonic_webview_destroy(pointer)
                return
            }
            val queuedActions = synchronized(stateLock) {
                api = native
                host = pointer
                pendingActions.toList().also { pendingActions.clear() }
            }
            native.harmonic_webview_set_visible(pointer, if (browserVisible) 1 else 0)
            queuedActions.forEach { action -> runNative(native, pointer, action) }
            startPolling()
        } catch (error: Throwable) {
            reportError(error)
        }
    }

    private fun scheduleBrowserResize() {
        if (disposed.get()) return
        resizeInProgress = true
        resizeSettleTimer.restart()
    }

    private fun resizeBrowserNow() {
        val bounds = browserBounds() ?: return
        withHost { native, pointer ->
            native.harmonic_webview_set_frame(
                pointer,
                bounds.x,
                bounds.top,
                bounds.width,
                bounds.height,
            )
        }
    }

    private fun browserBounds(): MacBrowserBounds? {
        val window = SwingUtilities.getWindowAncestor(this) ?: return null
        val content = SwingUtilities.getRootPane(this)?.contentPane ?: return null
        val location = SwingUtilities.convertPoint(this, 0, 0, content)
        return MacBrowserBounds(
            parentView = macAwtContentView(window),
            x = location.x.toDouble(),
            top = location.y.toDouble(),
            width = width.coerceAtLeast(1).toDouble(),
            height = height.coerceAtLeast(1).toDouble(),
        )
    }

    private fun startPolling() {
        if (!pollingStarted.compareAndSet(false, true)) return
        poller.scheduleWithFixedDelay(::pollSnapshot, 0, SNAPSHOT_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    private fun pollSnapshot() {
        if (disposed.get() || resizeInProgress) return
        val target = synchronized(stateLock) {
            api?.let { native -> host?.let { pointer -> native to pointer } }
        } ?: return
        runCatching {
            val state = IntArray(3)
            val url = ByteArray(TEXT_BUFFER_BYTES)
            val title = ByteArray(TEXT_BUFFER_BYTES)
            synchronized(nativeCallLock) {
                if (disposed.get()) return
                target.first.harmonic_webview_snapshot(
                    target.second,
                    state,
                    url,
                    url.size,
                    title,
                    title.size,
                )
            }
            DesktopBrowserSnapshot(
                url = url.decodeNullTerminatedUtf8(),
                title = title.decodeNullTerminatedUtf8().ifBlank { null },
                isLoading = state[0] != 0,
                canGoBack = state[1] != 0,
                canGoForward = state[2] != 0,
            )
        }.onSuccess { snapshot ->
            if (snapshot == lastSnapshot || disposed.get()) return@onSuccess
            lastSnapshot = snapshot
            EventQueue.invokeLater {
                if (!disposed.get()) onStateChanged(this, snapshot)
            }
        }.onFailure(::reportError)
    }

    private fun withHost(action: (MacWebViewApi, Pointer) -> Unit) {
        val target = synchronized(stateLock) {
            if (disposed.get()) return
            api?.let { native -> host?.let { pointer -> native to pointer } } ?: run {
                pendingActions += action
                null
            }
        }
        if (target != null) runNative(target.first, target.second, action)
    }

    private fun runNative(
        native: MacWebViewApi,
        pointer: Pointer,
        action: (MacWebViewApi, Pointer) -> Unit,
    ) {
        runCatching {
            synchronized(nativeCallLock) {
                if (!disposed.get()) action(native, pointer)
            }
        }.onFailure(::reportError)
    }

    private fun reportError(error: Throwable) {
        EventQueue.invokeLater {
            if (!disposed.get()) onError(error)
        }
    }

    private companion object {
        const val RESIZE_SETTLE_DELAY_MS = 100
        const val SNAPSHOT_INTERVAL_MS = 100L
        const val TEXT_BUFFER_BYTES = 64 * 1024
    }
}

internal interface MacWebViewApi : Library {
    fun harmonic_webview_create(
        parentView: Pointer,
        x: Double,
        top: Double,
        width: Double,
        height: Double,
    ): Pointer?
    fun harmonic_webview_destroy(host: Pointer)
    fun harmonic_webview_load_url(host: Pointer, url: String)
    fun harmonic_webview_go_back(host: Pointer)
    fun harmonic_webview_go_forward(host: Pointer)
    fun harmonic_webview_reload(host: Pointer, fallbackUrl: String)
    fun harmonic_webview_set_visible(host: Pointer, visible: Int)
    fun harmonic_webview_set_frame(
        host: Pointer,
        x: Double,
        top: Double,
        width: Double,
        height: Double,
    )
    fun harmonic_webview_evaluate_javascript(host: Pointer, script: String)
    fun harmonic_webview_snapshot(
        host: Pointer,
        state: IntArray,
        url: ByteArray,
        urlCapacity: Int,
        title: ByteArray,
        titleCapacity: Int,
    )
}

private data class MacBrowserBounds(
    val parentView: Pointer,
    val x: Double,
    val top: Double,
    val width: Double,
    val height: Double,
)

/**
 * JetBrains Runtime returns zero from JNA's generic macOS AWT handle lookup. Its public-internal
 * CPlatformView accessor is the stable pointer used by AWT itself for the window's NSView.
 */
private fun macAwtContentView(window: java.awt.Window): Pointer {
    val awtAccessor = Class.forName("sun.awt.AWTAccessor")
        .getMethod("getComponentAccessor")
        .invoke(null)
    val componentAccessorType = Class.forName("sun.awt.AWTAccessor\$ComponentAccessor")
    val peer = componentAccessorType
        .getMethod("getPeer", java.awt.Component::class.java)
        .invoke(awtAccessor, window)
        ?: error("The macOS AWT window peer is unavailable")
    val platformWindow = Class.forName("sun.lwawt.LWWindowPeer")
        .getMethod("getPlatformWindow")
        .invoke(peer)
        ?: error("The macOS platform window is unavailable")
    val contentView = Class.forName("sun.lwawt.macosx.CPlatformWindow")
        .getMethod("getContentView")
        .invoke(platformWindow)
        ?: error("The macOS AWT content view is unavailable")
    val pointer = Class.forName("sun.lwawt.macosx.CPlatformView")
        .getMethod("getAWTView")
        .invoke(contentView) as Long
    return Pointer(pointer)
}

private object MacWebViewLibrary {
    val api: MacWebViewApi by lazy {
        check(System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
            "The native WKWebView bridge can only be loaded on macOS"
        }
        val libraryName = System.mapLibraryName(LIBRARY_BASE_NAME)
        val classLoader = Thread.currentThread().contextClassLoader
            ?: MacWebViewLibrary::class.java.classLoader
        val bytes = classLoader.getResourceAsStream("native/$libraryName")?.use { it.readBytes() }
            ?: error("The native macOS WebView bridge is missing from this app build")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        val directory = Paths.get(System.getProperty("java.io.tmpdir"))
            .resolve("harmonic-mac-webview")
            .resolve(digest.take(16))
        val libraryPath = directory.resolve(libraryName)
        Files.createDirectories(directory)
        if (!Files.isRegularFile(libraryPath) || Files.size(libraryPath) != bytes.size.toLong()) {
            val temporary = Files.createTempFile(directory, libraryName, ".tmp")
            try {
                Files.write(temporary, bytes)
                try {
                    Files.move(
                        temporary,
                        libraryPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, libraryPath, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
        Native.load(
            libraryPath.toAbsolutePath().toString(),
            MacWebViewApi::class.java,
            mapOf(Library.OPTION_STRING_ENCODING to StandardCharsets.UTF_8.name()),
        )
    }

    private const val LIBRARY_BASE_NAME = "harmonic-mac-webview"
}

private fun ByteArray.decodeNullTerminatedUtf8(): String {
    val length = indexOf(0).let { if (it >= 0) it else size }
    return String(this, 0, length, StandardCharsets.UTF_8)
}
