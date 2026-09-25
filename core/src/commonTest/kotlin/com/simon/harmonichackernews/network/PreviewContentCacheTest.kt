package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.TestKeyValueStore
import com.simon.harmonichackernews.settings.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewContentCacheTest {
    @Test
    fun imageHitsBatchOrderWritesAndInsertionPersistsExactRecency() {
        val backing = TestKeyValueStore()
        var orderWrites = 0
        val store = object : KeyValueStore by backing {
            override fun putString(key: String, value: String?) {
                if (key == PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY) orderWrites++
                backing.putString(key, value)
            }
        }
        val cache = cache(maxDiskEntries = 100)
        repeat(100) { cache.savePreviewImage(store, "$it", "https://example.com/$it.png") }
        orderWrites = 0
        repeat(64) { cache.loadPreviewImage(store, "$it") }
        assertEquals(1, orderWrites)
        assertEquals("63", PreviewCachePolicy.decodeOrder(backing.getString(PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY)).last())

        cache.loadPreviewImage(store, "64")
        cache.savePreviewImage(store, "100", "https://example.com/100.png")
        assertFalse(cache.loadPreviewImage(store, "65", updateCacheOrder = false).loaded)
        val restoredOrder = PreviewCachePolicy.decodeOrder(backing.getString(PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY))
        assertEquals(listOf("64", "100"), restoredOrder.takeLast(2))
        assertEquals(100, restoredOrder.size)
    }

    @Test
    fun summaryEvictionRetainsRecentlyReadAndUpdatedEntries() {
        val store = TestKeyValueStore()
        val cache = PreviewContentCache(stableHash = { it }, maxSummaryEntries = 2)
        cache.saveLinkSummary(store, "one", LinkSummary(title = "one"))
        cache.saveLinkSummary(store, "two", LinkSummary(title = "two"))
        assertEquals("one", cache.loadLinkSummary(store, "one")?.title)
        cache.saveLinkSummary(store, "three", LinkSummary(title = "three"))
        // Remove persistent content to distinguish an in-memory hit from a disk reload.
        store.remove(PreviewCachePolicy.LINK_SUMMARY_PREFIX + "one")
        store.remove(PreviewCachePolicy.LINK_SUMMARY_PREFIX + "two")
        assertEquals("one", cache.loadLinkSummary(store, "one")?.title)
        assertNull(cache.loadLinkSummary(store, "two"))
        cache.saveLinkSummary(store, "three", LinkSummary(title = "updated"))
        assertEquals("one", cache.loadLinkSummary(store, "one")?.title)
        assertEquals("updated", cache.loadLinkSummary(store, "three")?.title)
    }

    @Test
    fun negativeImageHitsAndLruEvictionPersistAcrossCacheInstances() {
        val store = TestKeyValueStore()
        val cache = cache(maxDiskEntries = 2)

        cache.savePreviewImage(store, "1", null)
        cache.savePreviewImage(store, "2", "https://example.com/two.png")
        assertTrue(cache.loadPreviewImage(store, "1").loaded)
        cache.savePreviewImage(store, "3", "https://example.com/three.png")

        assertFalse(cache.loadPreviewImage(store, "2").loaded)
        assertTrue(cache.loadPreviewImage(store, "1", updateCacheOrder = false).loaded)
        assertNull(cache.loadPreviewImage(store, "1", updateCacheOrder = false).imageUrl)
        assertEquals(
            "https://example.com/three.png",
            cache().loadPreviewImage(store, "3", updateCacheOrder = false).imageUrl,
        )
    }

    @Test
    fun linkSummaryUsesSharedCodecAndStablePersistentKey() {
        val store = TestKeyValueStore()
        val summary = LinkSummary(
            title = "A shared cache",
            siteName = "Example",
            imageUrl = "https://example.com/image.png",
        )

        cache().saveLinkSummary(store, "https://example.com/article", summary)

        assertEquals(
            summary,
            cache().loadLinkSummary(store, "https://example.com/article"),
        )
        assertTrue(store.contains(PreviewCachePolicy.LINK_SUMMARY_PREFIX + "hash:https://example.com/article"))
    }

    @Test
    fun confirmedNegativeImageHitExpiresAndCanBeFetchedAgain() {
        val store = TestKeyValueStore()
        var now = 1_000L
        val cache = cache(negativeImageTtlMillis = 50L, nowMillis = { now })

        cache.savePreviewImage(store, "1", null)
        assertTrue(cache.loadPreviewImage(store, "1").loaded)

        now = 1_051L

        assertFalse(cache.loadPreviewImage(store, "1").loaded)
        assertFalse(store.contains(PreviewCachePolicy.PREVIEW_IMAGE_LOADED_PREFIX + "1"))
    }

    @Test
    fun loadingManyPersistedSummariesKeepsTheExistingMemoryBound() {
        val store = TestKeyValueStore()
        val writer = PreviewContentCache(stableHash = { it })
        for (id in 1..3) writer.saveLinkSummary(store, "page$id", LinkSummary(title = "Title $id"))
        val reader = PreviewContentCache(stableHash = { it }, maxSummaryEntries = 2)
        for (id in 1..3) assertEquals("Title $id", reader.loadLinkSummary(store, "page$id")?.title)
        store.remove(PreviewCachePolicy.LINK_SUMMARY_PREFIX + "page1")
        assertNull(reader.loadLinkSummary(store, "page1"))
        assertEquals("Title 3", reader.loadLinkSummary(store, "page3")?.title)
    }

    private fun cache(
        maxDiskEntries: Int = PreviewCachePolicy.MAX_DISK_ENTRIES,
        negativeImageTtlMillis: Long = 6L * 60L * 60L * 1_000L,
        nowMillis: () -> Long = { 1_000L },
    ) =
        PreviewContentCache(
            stableHash = { "hash:$it" },
            maxDiskEntries = maxDiskEntries,
            negativeImageTtlMillis = negativeImageTtlMillis,
            nowMillis = nowMillis,
        )
}
