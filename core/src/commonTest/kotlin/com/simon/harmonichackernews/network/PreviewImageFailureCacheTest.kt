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

    @Test
    fun repeatedLookupsObserveCooldownExpiryAndChangesFromOtherOwners() {
        var now = 1_000L
        val store = TestKeyValueStore()
        val cache = PreviewImageFailureCache(store, cooldownMillis = 100, nowMillis = { now })
        val other = PreviewImageFailureCache(store, cooldownMillis = 100, nowMillis = { now })
        assertFalse(cache.isFailed("image"))
        other.record("image", success = false)
        assertTrue(cache.isFailed("image"))

        now = 1_099L
        assertTrue(cache.isFailed("image"))
        now = 1_100L
        assertFalse(cache.isFailed("image"))
        other.record("image", success = false)
        assertTrue(cache.isFailed("image"))
        other.record("image", success = true)
        assertFalse(cache.isFailed("image"))

        cache.record("image", success = false)
        store.clear()
        cache.onStoreCleared()
        assertFalse(cache.isFailed("image"))
    }

    @Test
    fun keyCacheTurnoverPreservesPersistedFailures() {
        var now = 1_000L
        val store = TestKeyValueStore()
        val cache = PreviewImageFailureCache(store, maxEntries = 2, nowMillis = { now })
        val url = "https://example.com/image.png"
        val persistedKey = "preview_image_failure." + StableHash.sha256Hex(url)
        store.putLong(persistedKey, now)

        assertTrue(cache.isFailed(url))
        repeat(10) { assertFalse(cache.isFailed("other-$it")) }
        assertTrue(cache.isFailed(url))
        now--
        assertFalse(cache.isFailed(url))
        cache.record(url, success = true)
        assertFalse(store.contains(persistedKey))
    }
}
