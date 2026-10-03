package com.simon.harmonichackernews.widget

import com.simon.harmonichackernews.network.HttpBodyLimitException
import com.simon.harmonichackernews.network.HttpStatusException
import com.simon.harmonichackernews.network.WidgetFeedTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.net.ssl.SSLException

internal const val WIDGET_REFRESH_MAX_RETRIES = 3
internal const val WIDGET_REFRESH_BACKOFF_SECONDS = 30L

/** Only retry transient read failures, with a fresh budget for each manual/periodic refresh. */
internal fun shouldRetryWidgetRefresh(error: Throwable?, runAttemptCount: Int): Boolean {
    if (error == null || runAttemptCount >= WIDGET_REFRESH_MAX_RETRIES) return false
    val causes = generateSequence(error) { it.cause }.take(10).toList()
    if (causes.any { it is CancellationException || it is SSLException || it is HttpBodyLimitException }) return false
    return causes.any {
        it is IOException || it is HttpRequestTimeoutException || it is WidgetFeedTimeoutException ||
            it is HttpStatusException && (it.statusCode == 408 || it.statusCode == 429 || it.statusCode in 500..599)
    }
}

internal data class WidgetRefreshPresentation(val status: String, val message: String? = null)

internal fun widgetRefreshPresentation(
    refreshing: Boolean,
    failed: Boolean,
    retryScheduled: Boolean,
    hasStories: Boolean,
    updatedTime: String?,
    wide: Boolean,
): WidgetRefreshPresentation {
    val status = when {
        refreshing -> "Refreshing…"
        failed && retryScheduled -> "Retrying soon…"
        failed && hasStories -> updatedTime?.let { if (wide) "Saved $it" else it } ?: "Saved stories"
        failed -> "Refresh failed"
        updatedTime != null -> if (wide) "Updated $updatedTime" else updatedTime
        else -> "Loading…"
    }
    val message = if (failed && !refreshing) {
        val content = if (hasStories) "Showing saved stories." else "Couldn’t load stories."
        val recovery = if (retryScheduled) "Will retry automatically." else "Tap refresh to try again."
        "$content $recovery"
    } else null
    return WidgetRefreshPresentation(status, message)
}
