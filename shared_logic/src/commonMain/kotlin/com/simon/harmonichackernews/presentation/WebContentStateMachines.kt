package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.settings.WebViewPreferences
import com.simon.harmonichackernews.settings.TextPreferences
import com.simon.harmonichackernews.utils.AdHostBlocklist
import com.simon.harmonichackernews.utils.ArchiveRedirectPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
 * Platform-neutral reader-mode policy. Script evaluation, clocks, delays and user feedback remain
 * host facilities; toggle/default/pending and result transitions stay consistent on every shell.
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

    fun onEligiblePageLoadStarted(initiallyAvailable: Boolean) {
        state = state.copy(
            available = state.featureEnabled && state.integrated && initiallyAvailable,
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

class WebContentService {
    val adBlocklist = AdBlocklistService()
    fun createRuntime(): WebContentRuntime = WebContentRuntime(adBlocklist)
}
