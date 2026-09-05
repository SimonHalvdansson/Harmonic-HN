package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewImageFailureCacheTest {
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
