package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.toNetworkUrlOrNull
import com.simon.harmonichackernews.settings.WebViewPreloadMode
import com.simon.harmonichackernews.settings.WebViewPreferences
import com.simon.harmonichackernews.serialization.JsonStringCodec
import com.simon.harmonichackernews.utils.ArchiveRedirectPolicy

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

    fun externalBrowserUrl(
        currentUrl: String?,
        storyUrl: String?,
        platformUrls: WebContentPlatformUrls,
    ): String? {
        val current = currentUrl?.takeIf(String::isNotBlank)
        return if (classify(current, platformUrls) == WebContentPageKind.ERROR_PAGE) {
            storyUrl?.takeIf(String::isNotBlank)
        } else {
            current ?: storyUrl?.takeIf(String::isNotBlank)
        }
    }

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
    const val SUMMARY_PAGE_TEXT_SETTLE_MILLIS: Long = VISIBLE_LOAD_GRACE_MILLIS
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
    // This is deliberately much larger than the local summarizer's normal input while still
    // bounding the JSON string copied from a browser renderer into the host process.
    const val MAX_PAGE_TEXT_CHARS = 256 * 1024
    private const val MAX_ENCODED_RESULT_CHARS = MAX_PAGE_TEXT_CHARS * 6 + 2
    const val READ_COMMAND =
        "(function() { var text = document.body ? (document.body.innerText || '') : ''; " +
            "return text.length > $MAX_PAGE_TEXT_CHARS ? " +
            "text.slice(0, $MAX_PAGE_TEXT_CHARS) : text; })();"

    fun decode(result: String?): String {
        if (result == null || result == "null") return ""
        if (result.length > MAX_ENCODED_RESULT_CHARS) return ""
        return (JsonStringCodec.decodeJavascriptString(result)
            ?: result.removeSurrounding("\""))
            .take(MAX_PAGE_TEXT_CHARS)
    }
}

/** Cross-platform preload, redirect, cache-fallback, and error-page policy. */
object WebContentPolicy {
    fun validatedHttpUrl(url: String?): String? {
        val candidate = url?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (
            !candidate.startsWith("http://", ignoreCase = true) &&
            !candidate.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        val authority = candidate.substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        if (authority.isBlank()) return null
        val parsed = try {
            candidate.toNetworkUrlOrNull()
        } catch (_: Exception) {
            null
        } ?: return null
        if (
            parsed.host.isBlank() ||
            (!parsed.scheme.equals("http", ignoreCase = true) &&
                !parsed.scheme.equals("https", ignoreCase = true))
        ) {
            return null
        }
        return parsed.toString()
    }

    fun shouldPreload(
        mode: WebViewPreloadMode,
        minimumBatteryPercent: Int,
        environment: WebPreloadEnvironment,
    ): Boolean {
        val connectionAllowed = when (mode) {
            WebViewPreloadMode.ALWAYS -> true
            WebViewPreloadMode.WIFI_ONLY -> environment.unmeteredConnection
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
