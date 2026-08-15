package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.settings.AppFont
import com.simon.harmonichackernews.settings.TextPreferences
import com.simon.harmonichackernews.settings.WebViewPreferences
import com.simon.harmonichackernews.serialization.JsonStringCodec
import com.simon.harmonichackernews.utils.AdHostBlocklist
import com.simon.harmonichackernews.utils.ArchiveRedirectPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WebContentDriverState(
    val currentUrl: String? = null,
    val loading: Boolean = false,
    val pageReady: Boolean = false,
    val canGoBack: Boolean = false,
    val showingError: Boolean = false,
    val showingCachedContent: Boolean = false,
)

/**
 * Small browser boundary implemented by WebView, WKWebView and a desktop web engine.
 * No feature code needs to know which native view evaluates scripts or keeps history.
 */
interface WebContentDriver {
    val state: StateFlow<WebContentDriverState>
    fun load(url: String)
    fun reload()
    fun goBack(): Boolean
    fun evaluateJavaScript(script: String, onResult: (String?) -> Unit = {})
    fun readPageText(onResult: (String?) -> Unit)
}

data class WebPreloadEnvironment(
    val unmeteredConnection: Boolean,
    val batteryPercent: Int?,
)

data class WebContentUrlPlan(
    val originalUrl: String,
    val loadUrl: String,
    val archiveRedirected: Boolean,
)

enum class WebContentFailure { DNS, SSL, GENERIC, OFFLINE }

/** Relative paths in the common embedded-web resource bundle packaged by every host. */
object WebContentAssets {
    const val PDF_VIEWER_INDEX = "pdf/index.html"
    const val OFFLINE_PAGE = "webview_error.html"
    const val READABILITY_SCRIPT = "vendor/mozilla/readability/0.6.0/Readability.min.js"
    const val READER_MODE_SCRIPT = "reader_mode.js"
}

data class WebContentPlatformUrls(
    val pdfViewer: String,
    val errorPage: String,
)

enum class WebContentPageKind { EMPTY, CONTENT, PDF_VIEWER, ERROR_PAGE }

data class WebContentRoute(
    val url: String,
    val kind: WebContentPageKind,
    val pdfReference: String? = null,
)

/** URL/page classification shared by WebView, WKWebView and desktop browser hosts. */
object WebContentPagePolicy {
    fun classify(url: String?, platformUrls: WebContentPlatformUrls): WebContentPageKind = when {
        url.isNullOrBlank() -> WebContentPageKind.EMPTY
        url == platformUrls.pdfViewer -> WebContentPageKind.PDF_VIEWER
        url.startsWith(platformUrls.errorPage) -> WebContentPageKind.ERROR_PAGE
        else -> WebContentPageKind.CONTENT
    }

    fun isReaderEligible(url: String?, platformUrls: WebContentPlatformUrls): Boolean =
        classify(url, platformUrls) == WebContentPageKind.CONTENT

    fun route(
        url: String?,
        requestedPdfReference: String?,
        currentPdfReference: String?,
        platformUrls: WebContentPlatformUrls,
    ): WebContentRoute? {
        val resolvedUrl = url?.takeIf(String::isNotBlank) ?: return null
        val kind = classify(resolvedUrl, platformUrls)
        val pdfReference = if (kind == WebContentPageKind.PDF_VIEWER) {
            requestedPdfReference?.takeIf(String::isNotBlank)
                ?: currentPdfReference?.takeIf(String::isNotBlank)
                ?: return null
        } else {
            null
        }
        return WebContentRoute(resolvedUrl, kind, pdfReference)
    }

    fun errorPageUrl(
        failure: WebContentFailure,
        platformUrls: WebContentPlatformUrls,
    ): String = platformUrls.errorPage + "#" + WebContentPolicy.errorPageFragment(failure)

    fun cachedArticleBaseUrl(
        storedSourceUrl: String?,
        failingUrl: String?,
        storyUrl: String?,
    ): String? = storedSourceUrl?.takeIf(String::isNotBlank)
        ?: failingUrl?.takeIf(String::isNotBlank)
        ?: storyUrl?.takeIf(String::isNotBlank)
}

/** Timing policy shared by WebView, WKWebView and desktop browser adapters. */
object WebContentTiming {
    const val VISIBLE_LOAD_GRACE_MILLIS: Long = 1_500
    const val READER_INITIAL_AVAILABILITY_GRACE_MILLIS: Long = 2_000
    const val READER_AVAILABILITY_RECHECK_DELAY_MILLIS: Long = 2_500
    const val LOAD_TIMEOUT_MILLIS: Long = 45_000
    const val SUMMARY_LOAD_TIMEOUT_MILLIS: Long = 30_000
}

/** User-facing web-content copy kept identical across platform shells. */
object WebContentCopy {
    const val OPEN_URL_FAILED = "Couldn't open URL"
    const val AD_BLOCK_DISABLED = "Disabled AdBlock, refreshing WebView"
    const val READER_UNAVAILABLE_FOR_PAGE = "Reader mode unavailable for this page"
    const val READER_PENDING = "Reader mode will open after the page loads"
    const val READER_UNAVAILABLE = "Reader mode unavailable"
    const val READER_NO_ARTICLE = "Couldn't find readable article"
    const val READER_OPEN_FAILED = "Couldn't open reader mode"
    const val DOWNLOAD_LINK_FAILED = "Couldn't open download link"
    const val SHOWING_CACHED_CONTENT = "Showing cached webview content"
}

/** Shared page-text JavaScript and result decoding for every native browser adapter. */
object WebContentPageText {
    const val READ_COMMAND =
        "(function() { return document.body ? (document.body.innerText || '') : ''; })();"

    fun decode(result: String?): String {
        if (result == null || result == "null") return ""
        return JsonStringCodec.decodeJavascriptString(result)
            ?: result.removeSurrounding("\"")
    }
}

/** Pure assembly helpers; hosts only load bytes/assets and resolve native theme colors. */
object ReaderModeSourceAssembler {
    fun script(readabilitySource: String, readerModeSource: String): String = buildString {
        append(readabilitySource.trimEnd()).append('\n')
        append(readerModeSource.trimEnd()).append('\n')
    }

    fun cssColor(argb: Int): String = "#" + (argb and 0x00ff_ffff)
        .toString(16)
        .uppercase()
        .padStart(6, '0')

    fun fontDataUrl(base64: String): String = "data:font/ttf;base64,$base64"

    fun fontFaceCss(regularDataUrl: String, boldDataUrl: String): String {
        if (regularDataUrl.isBlank() || boldDataUrl.isBlank()) return ""
        return "@font-face{font-family:'HarmonicReaderFont';font-style:normal;" +
            "font-weight:400;src:url($regularDataUrl) format('truetype');}" +
            "@font-face{font-family:'HarmonicReaderFont';font-style:normal;" +
            "font-weight:700;src:url($boldDataUrl) format('truetype');}"
    }
}

enum class ReaderModeFontResource {
    PRODUCT_SANS_REGULAR,
    PRODUCT_SANS_BOLD,
    GOOGLE_SANS_FLEX_ROUNDED_REGULAR,
    GOOGLE_SANS_FLEX_ROUNDED_BOLD,
    GOOGLE_SANS_REGULAR,
    GOOGLE_SANS_BOLD,
    VERDANA_REGULAR,
    VERDANA_BOLD,
    ROBOTO_SLAB_REGULAR,
    ROBOTO_SLAB_BOLD,
    GOOGLE_SANS_CODE_REGULAR,
    JETBRAINS_MONO_REGULAR,
    JETBRAINS_MONO_BOLD,
    GEORGIA_REGULAR,
    GEORGIA_BOLD,
}

data class ReaderModeFontResources(
    val regular: ReaderModeFontResource,
    val bold: ReaderModeFontResource,
)

/** Canonical reader-font pairing; native hosts only load bytes for the selected resources. */
object ReaderModeFontResourcePolicy {
    fun resolve(storedFont: String?): ReaderModeFontResources? = when (
        AppFont.fromStored(storedFont)
    ) {
        AppFont.PRODUCT_SANS -> ReaderModeFontResources(
            ReaderModeFontResource.PRODUCT_SANS_REGULAR,
            ReaderModeFontResource.PRODUCT_SANS_BOLD,
        )
        AppFont.GOOGLE_SANS_FLEX_ROUNDED -> ReaderModeFontResources(
            ReaderModeFontResource.GOOGLE_SANS_FLEX_ROUNDED_REGULAR,
            ReaderModeFontResource.GOOGLE_SANS_FLEX_ROUNDED_BOLD,
        )
        AppFont.GOOGLE_SANS -> ReaderModeFontResources(
            ReaderModeFontResource.GOOGLE_SANS_REGULAR,
            ReaderModeFontResource.GOOGLE_SANS_BOLD,
        )
        AppFont.VERDANA -> ReaderModeFontResources(
            ReaderModeFontResource.VERDANA_REGULAR,
            ReaderModeFontResource.VERDANA_BOLD,
        )
        AppFont.ROBOTO_SLAB -> ReaderModeFontResources(
            ReaderModeFontResource.ROBOTO_SLAB_REGULAR,
            ReaderModeFontResource.ROBOTO_SLAB_BOLD,
        )
        AppFont.GOOGLE_SANS_CODE -> ReaderModeFontResources(
            ReaderModeFontResource.GOOGLE_SANS_CODE_REGULAR,
            ReaderModeFontResource.GOOGLE_SANS_CODE_REGULAR,
        )
        AppFont.JETBRAINS_MONO -> ReaderModeFontResources(
            ReaderModeFontResource.JETBRAINS_MONO_REGULAR,
            ReaderModeFontResource.JETBRAINS_MONO_BOLD,
        )
        AppFont.GEORGIA -> ReaderModeFontResources(
            ReaderModeFontResource.GEORGIA_REGULAR,
            ReaderModeFontResource.GEORGIA_BOLD,
        )
        AppFont.DEVICE_DEFAULT -> null
    }
}

/**
 * Portable grace/recheck bookkeeping for reader-mode availability. Native hosts supply only a
 * monotonic clock and their delayed-callback primitive.
 */
class ReaderModeAvailabilityCadence {
    private var initialGraceUsed = false
    private var initialGraceStartedAtMillis = 0L
    private var initialGraceGeneration = -1
    private var unavailableDelayGeneration = -1
    private var recheckGeneration = -1
    private var recheckUsed = false

    fun onLoadStarted(generation: Int, eligible: Boolean, nowMillis: Long) {
        recheckGeneration = -1
        recheckUsed = false
        unavailableDelayGeneration = -1
        if (!eligible) {
            initialGraceGeneration = -1
            return
        }
        val initialGraceStillActive = !initialGraceUsed || isGraceActive(nowMillis)
        if (!initialGraceStillActive) {
            initialGraceGeneration = -1
            return
        }
        if (!initialGraceUsed) {
            initialGraceUsed = true
            initialGraceStartedAtMillis = nowMillis
        }
        initialGraceGeneration = generation
    }

    fun onAvailable() {
        initialGraceGeneration = -1
        unavailableDelayGeneration = -1
        recheckGeneration = -1
    }

    fun onUnavailableNow() {
        initialGraceGeneration = -1
        unavailableDelayGeneration = -1
    }

    /** Returns a delay when unavailability should be deferred, or null when it applies now. */
    fun unavailableDelayMillis(generation: Int, nowMillis: Long): Long? {
        if (initialGraceGeneration != generation || !isGraceActive(nowMillis)) return null
        val remaining = WebContentTiming.READER_INITIAL_AVAILABILITY_GRACE_MILLIS -
            (nowMillis - initialGraceStartedAtMillis)
        if (remaining <= 0) return null
        unavailableDelayGeneration = generation
        return remaining
    }

    fun shouldApplyDelayedUnavailable(
        generation: Int,
        currentGeneration: Int,
    ): Boolean = unavailableDelayGeneration == generation &&
        generation == currentGeneration && initialGraceGeneration == generation

    fun scheduleRecheck(generation: Int, currentGeneration: Int): Boolean {
        if (recheckUsed || generation != currentGeneration) return false
        recheckUsed = true
        recheckGeneration = generation
        return true
    }

    fun shouldRunRecheck(generation: Int, currentGeneration: Int): Boolean =
        recheckGeneration == generation && generation == currentGeneration

    private fun isGraceActive(nowMillis: Long): Boolean = initialGraceGeneration >= 0 &&
        nowMillis - initialGraceStartedAtMillis <
        WebContentTiming.READER_INITIAL_AVAILABILITY_GRACE_MILLIS
}

/** Cross-platform preload, redirect, cache-fallback, and error-page policy. */
object WebContentPolicy {
    fun shouldPreload(
        mode: String?,
        minimumBatteryPercent: Int,
        environment: WebPreloadEnvironment,
    ): Boolean {
        val connectionAllowed = when (WebViewPreferences.sanitizePreloadMode(mode)) {
            WebViewPreferences.PRELOAD_ALWAYS -> true
            WebViewPreferences.PRELOAD_WIFI_ONLY -> environment.unmeteredConnection
            else -> false
        }
        if (!connectionAllowed) return false
        val minimum = WebViewPreferences.clampBatteryPercent(minimumBatteryPercent)
        return minimum == 0 || environment.batteryPercent == null ||
            environment.batteryPercent >= minimum
    }

    fun resolveUrl(url: String?, archiveDomains: Collection<String>): WebContentUrlPlan? {
        val original = url?.takeIf(String::isNotBlank) ?: return null
        val redirect = ArchiveRedirectPolicy.redirectUrl(original, archiveDomains)
        return WebContentUrlPlan(original, redirect ?: original, redirect != null)
    }

    fun shouldTryCachedArticle(failure: WebContentFailure): Boolean =
        failure == WebContentFailure.DNS || failure == WebContentFailure.OFFLINE

    fun errorPageFragment(failure: WebContentFailure): String = when (failure) {
        WebContentFailure.DNS -> "dns"
        WebContentFailure.SSL -> "ssl"
        WebContentFailure.GENERIC -> "generic"
        WebContentFailure.OFFLINE -> "offline"
    }
}

data class WebContentLoadState(
    val generation: Int = 0,
    val inProgress: Boolean = false,
    val uiSettled: Boolean = true,
    val committedVisible: Boolean = false,
)

/** Portable ordering rules for an embedded browser load. The platform only renders the result. */
class WebContentLoadStateMachine {
    var state: WebContentLoadState = WebContentLoadState()
        private set

    fun begin(): Int {
        state = WebContentLoadState(
            generation = state.generation + 1,
            inProgress = true,
            uiSettled = false,
        )
        return state.generation
    }

    fun commitVisible(generation: Int = state.generation): Boolean {
        if (!isCurrent(generation)) return false
        state = state.copy(committedVisible = true)
        return true
    }

    fun shouldSettleCommittedLoad(generation: Int): Boolean =
        isCurrent(generation) && state.inProgress && state.committedVisible

    fun isActive(generation: Int): Boolean = isCurrent(generation) && state.inProgress

    fun finish(generation: Int): Boolean {
        if (!isCurrent(generation)) return false
        state = state.copy(inProgress = false, uiSettled = true)
        return true
    }

    fun reset() {
        state = WebContentLoadState(generation = state.generation + 1)
    }

    private fun isCurrent(generation: Int): Boolean = generation == state.generation
}

enum class ReaderModeScriptStatus { ENABLED, DISABLED, NO_ARTICLE, UNAVAILABLE, FAILED }

data class ReaderModeState(
    val featureEnabled: Boolean = true,
    val integrated: Boolean = true,
    val defaultEnabled: Boolean = false,
    val available: Boolean = false,
    val enabled: Boolean = false,
    val pending: Boolean = false,
    val disabledForCurrentPage: Boolean = false,
)

sealed interface ReaderModeToggleDecision {
    data object Unavailable : ReaderModeToggleDecision
    data object LoadThenEnable : ReaderModeToggleDecision
    data class Apply(val enabled: Boolean) : ReaderModeToggleDecision
}

sealed interface ReaderModePageDecision {
    data object None : ReaderModePageDecision
    data object CheckAvailability : ReaderModePageDecision
    data class Apply(val enabled: Boolean, val showFeedback: Boolean) : ReaderModePageDecision
}

/**
 * Platform-neutral reader-mode policy. The embedded session supplies clocks and owns delayed
 * transition decisions; hosts only schedule the returned delays and evaluate native JavaScript.
 */
class ReaderModeStateMachine {
    var state: ReaderModeState = ReaderModeState()
        private set

    fun configure(featureEnabled: Boolean, integrated: Boolean, defaultEnabled: Boolean) {
        state = state.copy(
            featureEnabled = featureEnabled,
            integrated = integrated,
            defaultEnabled = featureEnabled && defaultEnabled,
            available = state.available && featureEnabled && integrated,
            enabled = state.enabled && featureEnabled && integrated,
        )
    }

    fun setIntegrated(integrated: Boolean) {
        state = state.copy(
            integrated = integrated,
            available = state.available && integrated,
            enabled = state.enabled && integrated,
            pending = state.pending && integrated,
        )
    }

    fun onEligiblePageLoadStarted() {
        state = state.copy(
            // Availability is re-evaluated after the page has loaded. Do not carry the
            // previous page's reader button through the first frames of a new load.
            available = false,
            enabled = false,
            disabledForCurrentPage = false,
        )
    }

    fun onIneligiblePageLoadStarted() {
        state = state.copy(
            available = false,
            enabled = false,
            pending = false,
            disabledForCurrentPage = false,
        )
    }

    fun toggle(pageEligible: Boolean, pageReady: Boolean, storyUrlAvailable: Boolean): ReaderModeToggleDecision {
        if (!state.featureEnabled || !state.integrated || !pageEligible) {
            return ReaderModeToggleDecision.Unavailable
        }
        val enable = !state.enabled
        state = state.copy(disabledForCurrentPage = !enable)
        if (!pageReady) {
            if (!storyUrlAvailable) return ReaderModeToggleDecision.Unavailable
            state = state.copy(pending = true)
            return ReaderModeToggleDecision.LoadThenEnable
        }
        return ReaderModeToggleDecision.Apply(enable)
    }

    fun disable(): ReaderModeToggleDecision.Apply? {
        if (!state.enabled) return null
        state = state.copy(disabledForCurrentPage = true)
        return ReaderModeToggleDecision.Apply(false)
    }

    fun onPageFinished(pageEligible: Boolean): ReaderModePageDecision {
        if (!pageEligible || !state.featureEnabled || !state.integrated) {
            onIneligiblePageLoadStarted()
            return ReaderModePageDecision.None
        }
        return when {
            state.pending -> {
                state = state.copy(pending = false)
                ReaderModePageDecision.Apply(enabled = true, showFeedback = true)
            }
            state.defaultEnabled && !state.disabledForCurrentPage && !state.enabled ->
                ReaderModePageDecision.Apply(enabled = true, showFeedback = false)
            else -> ReaderModePageDecision.CheckAvailability
        }
    }

    fun confirmAvailable() {
        state = state.copy(available = state.featureEnabled && state.integrated)
    }

    fun setUnavailable() {
        state = state.copy(available = false)
    }

    fun setEnabled(enabled: Boolean) {
        state = state.copy(enabled = enabled && state.featureEnabled && state.integrated)
    }

    fun applyResult(status: ReaderModeScriptStatus) {
        state = when (status) {
            ReaderModeScriptStatus.ENABLED -> state.copy(available = true, enabled = true)
            ReaderModeScriptStatus.DISABLED -> state.copy(available = true, enabled = false)
            ReaderModeScriptStatus.NO_ARTICLE,
            ReaderModeScriptStatus.UNAVAILABLE -> state.copy(available = false, enabled = false)
            ReaderModeScriptStatus.FAILED -> state
        }
    }
}

data class ReaderModeTheme(
    val light: Boolean,
    val backgroundColor: String,
    val textColor: String,
    val headingColor: String,
    val secondaryTextColor: String,
    val linkColor: String,
    val dividerColor: String,
    val codeBackgroundColor: String,
    val fontFaceCss: String = "",
    val font: String? = null,
    val fontSizePx: Int,
)

/** Shared reader-mode JavaScript protocol and theme serialization. */
object ReaderModeScriptProtocol {
    fun applyCommand(script: String, theme: ReaderModeTheme, enabled: Boolean): String =
        script + "\nHarmonicReaderMode.setTheme(${themeJson(theme)});" +
            "\nHarmonicReaderMode.${if (enabled) "enable" else "disable"}();"

    fun availabilityCommand(script: String): String =
        script + "\nHarmonicReaderMode.isAvailable();"

    fun parseStatus(result: String?): ReaderModeScriptStatus = when (normalize(result)) {
        "enabled" -> ReaderModeScriptStatus.ENABLED
        "disabled" -> ReaderModeScriptStatus.DISABLED
        "no_article" -> ReaderModeScriptStatus.NO_ARTICLE
        "unavailable" -> ReaderModeScriptStatus.UNAVAILABLE
        else -> ReaderModeScriptStatus.FAILED
    }

    fun isAvailable(result: String?): Boolean = normalize(result) == "available"

    fun fontFamily(font: String?): String = when (TextPreferences.sanitizeFont(font)) {
        "robotoslab", "georgia" -> "Georgia, 'Times New Roman', serif"
        "jetbrainsmono", "googlesanscode" ->
            "ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', monospace"
        else -> "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
    }

    private fun themeJson(theme: ReaderModeTheme): String {
        val fallback = fontFamily(theme.font)
        val family = if (theme.fontFaceCss.isBlank()) fallback else "'HarmonicReaderFont', $fallback"
        return buildString {
            append("{\"isLight\":").append(theme.light)
            append(",\"backgroundColor\":").append(json(theme.backgroundColor))
            append(",\"textColor\":").append(json(theme.textColor))
            append(",\"headingColor\":").append(json(theme.headingColor))
            append(",\"secondaryTextColor\":").append(json(theme.secondaryTextColor))
            append(",\"linkColor\":").append(json(theme.linkColor))
            append(",\"dividerColor\":").append(json(theme.dividerColor))
            append(",\"codeBackgroundColor\":").append(json(theme.codeBackgroundColor))
            append(",\"fontFaceCss\":").append(json(theme.fontFaceCss))
            append(",\"fontFamily\":").append(json(family))
            append(",\"headingFontFamily\":").append(json(family))
            append(",\"fontSizePx\":").append(theme.fontSizePx).append('}')
        }
    }

    private fun normalize(result: String?): String = result.orEmpty().trim()
        .let { if (it.length >= 2 && it.startsWith('"') && it.endsWith('"')) it.substring(1, it.lastIndex) else it }

    private fun json(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\', '"' -> append('\\').append(character)
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }
}

/** Application-scoped decoded ad-host data without a platform singleton. */
class AdBlocklistService {
    private val mutableHosts = MutableStateFlow(AdHostBlocklist.empty())
    val hosts: StateFlow<AdHostBlocklist> = mutableHosts.asStateFlow()

    fun install(encoded: ByteArray): Int {
        val decoded = AdHostBlocklist.decode(encoded)
        mutableHosts.value = decoded
        return decoded.size
    }

    fun contains(host: String?): Boolean = mutableHosts.value.contains(host)
}

/** Per-browser portable runtime; native hosts retain rendering and JavaScript evaluation. */
class WebContentRuntime internal constructor(
    val adBlocklist: AdBlocklistService,
) {
    val load = WebContentLoadStateMachine()
    val reader = ReaderModeStateMachine()
}

enum class EmbeddedWebContentPage { CONTENT, ERROR, CACHED_CONTENT }

data class EmbeddedWebContentSessionState(
    val page: EmbeddedWebContentPage = EmbeddedWebContentPage.CONTENT,
    val lastRequestedUrl: String? = null,
    val lastFailedUrl: String? = null,
    val retryingFailedUrl: Boolean = false,
)

data class ReaderModeStateChange(
    val previous: ReaderModeState,
    val current: ReaderModeState,
) {
    val availabilityChanged: Boolean get() = previous.available != current.available
    val enabledChanged: Boolean get() = previous.enabled != current.enabled
}

data class EmbeddedWebLoadStart(
    val generation: Int,
    val readerMode: ReaderModeStateChange,
)

data class ReaderModeToggleResult(
    val decision: ReaderModeToggleDecision,
    val change: ReaderModeStateChange,
)

data class ReaderModePageResult(
    val decision: ReaderModePageDecision,
    val change: ReaderModeStateChange,
)

data class ReaderModeEvaluationResult(
    val change: ReaderModeStateChange,
    val message: String? = null,
    val delayedUnavailableMillis: Long? = null,
)

data class ReaderModeAvailabilityResult(
    val change: ReaderModeStateChange,
    val delayedUnavailableMillis: Long? = null,
    val scheduleRecheck: Boolean = false,
)

data class EmbeddedWebFailurePlan(
    val failedUrl: String?,
    val tryCachedArticle: Boolean,
    val errorPageFragment: String,
)

/**
 * Portable orchestration around an embedded browser. Native hosts render pages, load assets,
 * evaluate JavaScript and schedule delays; load ordering, reader-mode decisions, failure recovery,
 * retry ownership, and stale page-text request rejection live here.
 */
class EmbeddedWebContentSession(
    private val runtime: WebContentRuntime,
    driver: WebContentDriver,
) {
    val controller = WebContentController(runtime, driver)

    val readerState: ReaderModeState get() = runtime.reader.state

    var state: EmbeddedWebContentSessionState = EmbeddedWebContentSessionState()
        private set

    private val pageTextRequests = KeyedRequestSession<Unit>()
    private var pageTextReadGeneration = -1
    private val readerAvailability = ReaderModeAvailabilityCadence()

    fun configureReader(
        featureEnabled: Boolean,
        integrated: Boolean,
        defaultEnabled: Boolean,
    ): ReaderModeStateChange = readerChange {
        runtime.reader.configure(featureEnabled, integrated, defaultEnabled)
    }

    fun setReaderIntegrated(integrated: Boolean): ReaderModeStateChange = readerChange {
        runtime.reader.setIntegrated(integrated)
    }

    fun onLoadStarted(pageEligible: Boolean, nowMillis: Long): EmbeddedWebLoadStart {
        val generation = runtime.load.begin()
        val readerEligible = readerState.featureEnabled && readerState.integrated && pageEligible
        readerAvailability.onLoadStarted(
            generation = generation,
            eligible = readerEligible,
            nowMillis = nowMillis,
        )
        val change = readerChange {
            if (readerEligible) {
                runtime.reader.onEligiblePageLoadStarted()
            } else {
                runtime.reader.onIneligiblePageLoadStarted()
            }
        }
        return EmbeddedWebLoadStart(generation, change)
    }

    fun onPageFinished(pageEligible: Boolean): ReaderModePageResult {
        var decision: ReaderModePageDecision = ReaderModePageDecision.None
        val change = readerChange { decision = runtime.reader.onPageFinished(pageEligible) }
        finishRetry()
        return ReaderModePageResult(decision, change)
    }

    fun toggleReaderMode(
        pageEligible: Boolean,
        pageReady: Boolean,
        storyUrlAvailable: Boolean,
    ): ReaderModeToggleResult {
        var decision: ReaderModeToggleDecision = ReaderModeToggleDecision.Unavailable
        val change = readerChange {
            decision = runtime.reader.toggle(pageEligible, pageReady, storyUrlAvailable)
        }
        return ReaderModeToggleResult(decision, change)
    }

    fun disableReaderMode(): ReaderModeToggleResult? {
        var decision: ReaderModeToggleDecision.Apply? = null
        val change = readerChange { decision = runtime.reader.disable() }
        return decision?.let { ReaderModeToggleResult(it, change) }
    }

    fun applyReaderModeEvaluation(
        status: ReaderModeScriptStatus,
        generation: Int,
        nowMillis: Long,
        showFeedback: Boolean,
    ): ReaderModeEvaluationResult {
        var delayedUnavailableMillis: Long? = null
        val change = when (status) {
            ReaderModeScriptStatus.ENABLED,
            ReaderModeScriptStatus.DISABLED -> {
                readerAvailability.onAvailable()
                readerChange { runtime.reader.applyResult(status) }
            }
            ReaderModeScriptStatus.NO_ARTICLE,
            ReaderModeScriptStatus.UNAVAILABLE -> readerChange {
                delayedUnavailableMillis = setUnavailableRespectingInitialGrace(
                    generation,
                    nowMillis,
                )
                runtime.reader.setEnabled(false)
            }
            ReaderModeScriptStatus.FAILED -> readerChange {
                delayedUnavailableMillis = setUnavailableRespectingInitialGrace(
                    generation,
                    nowMillis,
                )
            }
        }
        val message = if (!showFeedback) null else when (status) {
            ReaderModeScriptStatus.NO_ARTICLE -> WebContentCopy.READER_NO_ARTICLE
            ReaderModeScriptStatus.UNAVAILABLE -> WebContentCopy.READER_UNAVAILABLE_FOR_PAGE
            ReaderModeScriptStatus.FAILED -> WebContentCopy.READER_OPEN_FAILED
            ReaderModeScriptStatus.ENABLED,
            ReaderModeScriptStatus.DISABLED -> null
        }
        return ReaderModeEvaluationResult(change, message, delayedUnavailableMillis)
    }

    fun applyReaderAvailability(
        available: Boolean,
        generation: Int,
        nowMillis: Long,
    ): ReaderModeAvailabilityResult {
        var delayedUnavailableMillis: Long? = null
        val change = readerChange {
            if (available) {
                readerAvailability.onAvailable()
                runtime.reader.confirmAvailable()
            } else {
                delayedUnavailableMillis = setUnavailableRespectingInitialGrace(
                    generation,
                    nowMillis,
                )
            }
        }
        return ReaderModeAvailabilityResult(
            change = change,
            delayedUnavailableMillis = delayedUnavailableMillis,
            scheduleRecheck = !available && readerAvailability.scheduleRecheck(
                generation,
                controller.loadState.generation,
            ),
        )
    }

    fun setReaderUnavailableNow(): ReaderModeStateChange = readerChange {
        readerAvailability.onUnavailableNow()
        runtime.reader.setUnavailable()
    }

    fun applyDelayedReaderUnavailable(generation: Int): ReaderModeStateChange? {
        if (!readerAvailability.shouldApplyDelayedUnavailable(
                generation,
                controller.loadState.generation,
            )
        ) return null
        return setReaderUnavailableNow()
    }

    fun shouldRunReaderAvailabilityRecheck(generation: Int): Boolean =
        readerAvailability.shouldRunRecheck(generation, controller.loadState.generation)

    fun recordRequestedUrl(url: String?, preservePage: Boolean = false) {
        val requested = url?.takeIf(String::isNotBlank) ?: return
        state = state.copy(
            page = if (preservePage) state.page else EmbeddedWebContentPage.CONTENT,
            lastRequestedUrl = requested,
        )
    }

    fun recordFailure(failingUrl: String?, currentUrl: String?): String? {
        val failed = resolveFailedUrl(failingUrl, currentUrl)
        state = state.copy(
            lastFailedUrl = failed,
            retryingFailedUrl = false,
        )
        return failed
    }

    fun planFailure(
        failure: WebContentFailure,
        failingUrl: String?,
        currentUrl: String?,
    ): EmbeddedWebFailurePlan = EmbeddedWebFailurePlan(
        failedUrl = resolveFailedUrl(failingUrl, currentUrl),
        tryCachedArticle = WebContentPolicy.shouldTryCachedArticle(failure),
        errorPageFragment = WebContentPolicy.errorPageFragment(failure),
    )

    fun showError(plan: EmbeddedWebFailurePlan) {
        state = state.copy(
            page = EmbeddedWebContentPage.ERROR,
            lastFailedUrl = plan.failedUrl,
            retryingFailedUrl = false,
        )
    }

    fun showContent() {
        state = state.copy(page = EmbeddedWebContentPage.CONTENT)
    }

    fun showError() {
        state = state.copy(page = EmbeddedWebContentPage.ERROR)
    }

    fun showCachedContent(failedUrl: String?, currentUrl: String?) {
        recordFailure(failedUrl, currentUrl)
        state = state.copy(page = EmbeddedWebContentPage.CACHED_CONTENT)
    }

    fun beginRetry(): String? {
        val failed = state.lastFailedUrl?.takeIf(String::isNotBlank) ?: return null
        state = state.copy(retryingFailedUrl = true)
        return failed
    }

    fun finishRetry() {
        state = state.copy(retryingFailedUrl = false)
    }

    fun beginPageTextRequest(): Int {
        pageTextReadGeneration = -1
        return pageTextRequests.begin(Unit)
    }

    fun currentPageTextRequestGeneration(): Int = pageTextRequests.generation

    fun isCurrentPageTextRequest(generation: Int): Boolean =
        pageTextRequests.isCurrent(generation, Unit)

    /** Claims a page-text result once, so a timeout and native callback cannot both complete it. */
    fun claimPageTextRequest(generation: Int): Boolean {
        if (!isCurrentPageTextRequest(generation)) return false
        pageTextRequests.invalidate()
        pageTextReadGeneration = -1
        return true
    }

    fun readRequestedPageText(generation: Int, onResult: (String?) -> Unit): Boolean {
        if (!isCurrentPageTextRequest(generation) || pageTextReadGeneration == generation) {
            return false
        }
        pageTextReadGeneration = generation
        controller.readPageText(onResult)
        return true
    }

    fun reset() {
        controller.reset()
        pageTextRequests.invalidate()
        pageTextReadGeneration = -1
        state = EmbeddedWebContentSessionState()
    }

    private fun setUnavailableRespectingInitialGrace(
        generation: Int,
        nowMillis: Long,
    ): Long? {
        val delay = readerAvailability.unavailableDelayMillis(generation, nowMillis)
        if (delay == null) {
            readerAvailability.onUnavailableNow()
            runtime.reader.setUnavailable()
        }
        return delay
    }

    private fun resolveFailedUrl(failingUrl: String?, currentUrl: String?): String? =
        failingUrl?.takeIf(String::isNotBlank)
            ?: state.lastRequestedUrl
            ?: currentUrl?.takeIf(String::isNotBlank)

    private inline fun readerChange(change: () -> Unit): ReaderModeStateChange {
        val previous = runtime.reader.state
        change()
        val current = runtime.reader.state
        if (!previous.available && current.available) readerAvailability.onAvailable()
        return ReaderModeStateChange(previous, current)
    }
}

/**
 * Portable command controller layered over a native [WebContentDriver]. URL decisions, reader
 * protocol and state-machine transitions therefore stay identical across hosts.
 */
class WebContentController(
    private val runtime: WebContentRuntime,
    private val driver: WebContentDriver,
) {
    val driverState: StateFlow<WebContentDriverState> get() = driver.state
    val loadState: WebContentLoadState get() = runtime.load.state
    val readerState: ReaderModeState get() = runtime.reader.state

    fun load(url: String?, archiveDomains: Collection<String>): WebContentUrlPlan? {
        val plan = WebContentPolicy.resolveUrl(url, archiveDomains) ?: return null
        driver.load(plan.loadUrl)
        return plan
    }

    fun reload() = driver.reload()

    fun goBack(): Boolean = driver.goBack()

    fun readPageText(onResult: (String?) -> Unit) = driver.readPageText(onResult)

    fun onPageCommitVisible(generation: Int = runtime.load.state.generation): Boolean =
        runtime.load.commitVisible(generation)
    fun onLoadFinished(generation: Int = runtime.load.state.generation): Boolean =
        runtime.load.finish(generation)
    fun reset() = runtime.load.reset()

    fun evaluateReaderMode(
        script: String,
        theme: ReaderModeTheme,
        enabled: Boolean,
        onResult: (ReaderModeScriptStatus) -> Unit,
    ) {
        driver.evaluateJavaScript(ReaderModeScriptProtocol.applyCommand(script, theme, enabled)) {
            val status = ReaderModeScriptProtocol.parseStatus(it)
            onResult(status)
        }
    }

    fun evaluateReaderModeAvailability(script: String, onResult: (Boolean) -> Unit) {
        driver.evaluateJavaScript(ReaderModeScriptProtocol.availabilityCommand(script)) {
            val available = ReaderModeScriptProtocol.isAvailable(it)
            onResult(available)
        }
    }
}

class WebContentService {
    val adBlocklist = AdBlocklistService()
    fun createRuntime(): WebContentRuntime = WebContentRuntime(adBlocklist)
}
