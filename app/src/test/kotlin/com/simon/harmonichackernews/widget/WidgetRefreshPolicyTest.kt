package com.simon.harmonichackernews.widget

import com.simon.harmonichackernews.network.HttpBodyLimitException
import com.simon.harmonichackernews.network.HttpStatusException
import com.simon.harmonichackernews.network.WidgetFeedTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class WidgetRefreshPolicyTest {
    @Test
    fun retriesDnsConnectionAndDeadlineFailuresIncludingWrappedCauses() {
        val errors = listOf(
            UnknownHostException("Unable to resolve host"),
            SocketException("Software caused connection abort"),
            SocketTimeoutException("Read timed out"),
            HttpRequestTimeoutException("https://news.ycombinator.com", 100L),
            WidgetFeedTimeoutException("Widget deadline expired"),
            IllegalStateException("Request failed", UnknownHostException()),
        )
        errors.forEach { error ->
            for (attempt in 0..2) assertTrue("$error attempt=$attempt", shouldRetryWidgetRefresh(error, attempt))
            assertFalse(shouldRetryWidgetRefresh(error, 3))
            assertFalse(shouldRetryWidgetRefresh(error, 4))
        }
    }

    @Test
    fun retriesOnlyTemporaryHttpFailures() {
        listOf(408, 429, 500, 502, 503, 504).forEach { status ->
            assertTrue(shouldRetryWidgetRefresh(HttpStatusException(status, "Unavailable", "https://example.com"), 0))
        }
        listOf(400, 401, 403, 404, 422).forEach { status ->
            assertFalse(shouldRetryWidgetRefresh(HttpStatusException(status, "Rejected", "https://example.com"), 0))
        }
    }

    @Test
    fun doesNotRetryCancellationPermanentFailuresOrMissingItems() {
        listOf(
            null,
            IllegalStateException("No items returned"),
            IllegalArgumentException("Invalid response"),
            SSLHandshakeException("Certificate rejected"),
            HttpBodyLimitException(100, 101),
            CancellationException("Worker replaced").apply { initCause(SocketException()) },
            IllegalStateException("Cancelled", CancellationException()),
        ).forEach { assertFalse(shouldRetryWidgetRefresh(it, 0)) }
    }

    @Test
    fun distinguishesPendingRecoveryFromExhaustedRetriesWithSavedStories() {
        assertEquals(
            WidgetRefreshPresentation("Retrying soon…", "Showing saved stories. Will retry automatically."),
            presentation(failed = true, retryScheduled = true),
        )
        assertEquals(
            WidgetRefreshPresentation("Saved 17:30", "Showing saved stories. Tap refresh to try again."),
            presentation(failed = true),
        )
        assertEquals("17:30", presentation(failed = true, wide = false).status)
        assertEquals("Saved stories", presentation(failed = true, updatedTime = null).status)
    }

    @Test
    fun showsRecoveryInstructionsWhenThereAreNoSavedStories() {
        assertEquals(
            WidgetRefreshPresentation("Retrying soon…", "Couldn’t load stories. Will retry automatically."),
            presentation(failed = true, retryScheduled = true, hasStories = false, updatedTime = null),
        )
        assertEquals(
            WidgetRefreshPresentation("Refresh failed", "Couldn’t load stories. Tap refresh to try again."),
            presentation(failed = true, hasStories = false, updatedTime = null),
        )
    }

    @Test
    fun successfulAndActiveRefreshesClearRecoveryMessages() {
        assertEquals(WidgetRefreshPresentation("Updated 17:30"), presentation())
        assertEquals(WidgetRefreshPresentation("Loading…"), presentation(hasStories = false, updatedTime = null))
        val refreshing = presentation(refreshing = true, failed = true, retryScheduled = true)
        assertEquals("Refreshing…", refreshing.status)
        assertNull(refreshing.message)
    }

    private fun presentation(
        refreshing: Boolean = false,
        failed: Boolean = false,
        retryScheduled: Boolean = false,
        hasStories: Boolean = true,
        updatedTime: String? = "17:30",
        wide: Boolean = true,
    ) = widgetRefreshPresentation(refreshing, failed, retryScheduled, hasStories, updatedTime, wide)
}
