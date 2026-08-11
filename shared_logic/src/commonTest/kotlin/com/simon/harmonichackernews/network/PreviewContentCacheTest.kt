package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewContentCacheTest {
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
    fun tintMemoryAndPersistentLruArePlatformNeutral() {
        val store = TestKeyValueStore(
            mapOf(
                PreviewCachePolicy.PREVIEW_TINT_ORDER_KEY to "legacy-tint-key",
                "legacy-tint-key" to 123,
            ),
        )
        val cache = cache()

        cache.saveTintColor(
            store = store,
            storyId = 42,
            imageUrl = "https://example.com/image.png",
            baseColor = 10,
            tintColor = 20,
        )

        assertFalse(store.contains("legacy-tint-key"))
        assertEquals(1, cache.tintColorCount(store))
        assertEquals(20, cache.loadTintColor(null, 42, "https://example.com/image.png", 10))
        assertEquals(
            20,
            cache().loadTintColor(store, 42, "https://example.com/image.png", 10),
        )
    }

    private fun cache(maxDiskEntries: Int = PreviewCachePolicy.MAX_DISK_ENTRIES) =
        PreviewContentCache(
            stableHash = { "hash:$it" },
            maxDiskEntries = maxDiskEntries,
        )
}
