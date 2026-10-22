package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewImageFailureCacheTest {
    @Test
    fun offlineFailuresDoNotSurviveRestartOrSuppressOnlineRetries() {
        var online = false
        val store = TestKeyValueStore()
        val cache = PreviewImageFailureCache(store, isOnline = { online })
        cache.record("offline-image", success = false)
        assertTrue(cache.isFailed("offline-image"))
        assertFalse(PreviewImageFailureCache(store).isFailed("offline-image"))

        online = true
        assertFalse(cache.isFailed("offline-image"))
        val version = cache.changes.value
        cache.retryOfflineFailures()
        assertEquals(version + 1, cache.changes.value)
        online = false
        assertFalse(cache.isFailed("offline-image"))
    }

    @Test
    fun reconnectOnlyRetriesOfflineFailuresAndKeepsOnlineCooldown() {
        var online = true
        val cache = PreviewImageFailureCache(TestKeyValueStore(), isOnline = { online })
        cache.record("broken-image", success = false)
        online = false
        cache.record("offline-image", success = false)
        cache.retryOfflineFailures()
        assertTrue(cache.isFailed("offline-image"))

        online = true
        cache.retryOfflineFailures()
        assertFalse(cache.isFailed("offline-image"))
        assertTrue(cache.isFailed("broken-image"))
    }

    @Test
    fun failuresAreBoundedPersistedAndClearedBySuccess() {
        val store = TestKeyValueStore()
        val cache = PreviewImageFailureCache(store, maxEntries = 2)
        cache.record("one", success = false)
        cache.record("two", success = false)
        cache.record("three", success = false)

        val reopened = PreviewImageFailureCache(store)
        assertFalse(reopened.isFailed("one"))
        assertTrue(reopened.isFailed("two"))
        assertTrue(reopened.isFailed("three"))
        reopened.record("two", success = true)
        assertFalse(cache.isFailed("two"))
    }

    @Test
    fun clockRollbackDoesNotLeaveAnImageSuppressed() {
        var now = 1_000L
        val cache = PreviewImageFailureCache(TestKeyValueStore(), nowMillis = { now })
        cache.record("image", success = false)
        now = 999L
        assertFalse(cache.isFailed("image"))
    }
}
