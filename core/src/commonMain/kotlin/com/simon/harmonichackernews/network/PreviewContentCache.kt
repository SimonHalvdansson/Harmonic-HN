package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.KeyValueStore
import kotlin.time.Clock

data class CachedPreviewImageUrl(
    val loaded: Boolean,
    val imageUrl: String?,
)

/**
 * Platform-neutral preview metadata cache.
 *
 * A platform supplies its existing key-value store and stable hash implementation. The caller is
 * responsible for serializing access when the backing store can be reached from multiple threads.
 * This keeps persistence format, negative hits, LRU eviction and in-memory summary state
 * identical on Android, iOS and desktop without imposing a filesystem implementation on common.
 */
class PreviewContentCache(
    private val stableHash: (String) -> String = StableHash::sha256Hex,
    private val maxDiskEntries: Int = PreviewCachePolicy.MAX_DISK_ENTRIES,
    private val maxSummaryEntries: Int = 300,
    private val negativeImageTtlMillis: Long = PreviewCachePolicy.NEGATIVE_IMAGE_TTL_MILLIS,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val summaries = linkedMapOf<String, LinkSummary>()
    private val cacheOrders = mutableMapOf<String, CacheOrder>()

    fun loadPreviewImage(
        store: KeyValueStore?,
        entryId: String?,
        updateCacheOrder: Boolean = true,
    ): CachedPreviewImageUrl {
        if (store == null || entryId.isNullOrEmpty()) return CachedPreviewImageUrl(false, null)
        val imageUrl = store.getString(previewImageUrlKey(entryId), null)
        var loaded = store.getBoolean(previewImageLoadedKey(entryId), false) ||
            !imageUrl.isNullOrEmpty()
        if (loaded && imageUrl.isNullOrEmpty()) {
            val cachedAt = store.getLong(previewImageMissTimeKey(entryId), 0L)
            val now = nowMillis()
            loaded = cachedAt > 0L && now >= cachedAt && now - cachedAt <= negativeImageTtlMillis
            if (!loaded && updateCacheOrder) invalidatePreviewImage(store, entryId)
        }
        if (updateCacheOrder && loaded) touch(store, PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY, entryId)
        return CachedPreviewImageUrl(loaded, imageUrl)
    }

    fun savePreviewImage(store: KeyValueStore?, entryId: String?, imageUrl: String?) {
        if (store == null || entryId.isNullOrEmpty()) return
        val order = readOrder(store, PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY)
        val evicted = order.touch(entryId, maxDiskEntries)
        store.update {
            putBoolean(previewImageLoadedKey(entryId), true)
            if (imageUrl.isNullOrEmpty()) {
                remove(previewImageUrlKey(entryId))
                putLong(previewImageMissTimeKey(entryId), nowMillis())
            } else {
                putString(previewImageUrlKey(entryId), imageUrl)
                remove(previewImageMissTimeKey(entryId))
            }
            evicted.forEach { oldestId ->
                remove(previewImageUrlKey(oldestId))
                remove(previewImageLoadedKey(oldestId))
                remove(previewImageMissTimeKey(oldestId))
            }
            putString(
                PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY,
                order.encode(),
            )
        }
        order.pendingTouches = 0
    }

    fun invalidatePreviewImage(store: KeyValueStore?, entryId: String?) {
        if (store == null || entryId.isNullOrEmpty()) return
        val order = readOrder(store, PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY)
        order.remove(entryId)
        store.update {
            remove(previewImageUrlKey(entryId))
            remove(previewImageLoadedKey(entryId))
            remove(previewImageMissTimeKey(entryId))
            putString(
                PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY,
                order.encode(),
            )
        }
        order.pendingTouches = 0
    }

    fun loadLinkSummary(store: KeyValueStore?, normalizedUrl: String?): LinkSummary? {
        if (normalizedUrl.isNullOrEmpty()) return null
        summaries.remove(normalizedUrl)?.let {
            summaries[normalizedUrl] = it
            return it
        }
        if (store == null) return null
        val key = linkSummaryKey(normalizedUrl)
        val result = LinkSummaryCodec.decode(store.getString(key, null)) ?: return null
        rememberSummary(normalizedUrl, result)
        touch(store, PreviewCachePolicy.LINK_SUMMARY_ORDER_KEY, key)
        return result
    }

    fun saveLinkSummary(
        store: KeyValueStore?,
        normalizedUrl: String?,
        summary: LinkSummary?,
    ) {
        if (normalizedUrl.isNullOrEmpty() || summary == null) return
        rememberSummary(normalizedUrl, summary)
        if (store == null) return

        val key = linkSummaryKey(normalizedUrl)
        val order = readOrder(store, PreviewCachePolicy.LINK_SUMMARY_ORDER_KEY)
        val evicted = order.touch(key, maxDiskEntries)
        store.update {
            putString(key, LinkSummaryCodec.encode(summary))
            evicted.forEach(::remove)
            putString(PreviewCachePolicy.LINK_SUMMARY_ORDER_KEY, order.encode())
        }
        order.pendingTouches = 0
    }

    /** Clears process memory and invalidates cached order snapshots after platform storage cleanup. */
    fun reset() {
        summaries.clear()
        cacheOrders.clear()
    }

    private fun touch(store: KeyValueStore, orderKey: String, key: String) {
        val order = readOrder(store, orderKey)
        order.touch(key, maxDiskEntries)
        // Exact recency is retained in memory. Persist at most once per batch of hits; every
        // insertion/removal also persists it. Process death can lose only recent hit ordering,
        // never cache content or the entry bound.
        if (order.pendingTouches >= 64) {
            store.putString(orderKey, order.encode())
            order.pendingTouches = 0
        }
    }

    private fun readOrder(store: KeyValueStore, orderKey: String): CacheOrder =
        cacheOrders.getOrPut(orderKey) {
            CacheOrder(PreviewCachePolicy.decodeOrder(store.getString(orderKey, "")))
        }

    private fun rememberSummary(url: String, summary: LinkSummary) {
        if (maxSummaryEntries <= 0) return
        summaries.remove(url)
        while (summaries.size >= maxSummaryEntries) summaries.remove(summaries.keys.first())
        summaries[url] = summary
    }

    private class CacheOrder(keys: List<String>) {
        private val entries = keys.toCollection(linkedSetOf())
        private var newest: String? = keys.lastOrNull()
        var pendingTouches = 0

        fun touch(key: String, capacity: Int): List<String> {
            if (newest == key && entries.size <= capacity) return emptyList()
            entries.remove(key)
            entries.add(key)
            newest = key
            pendingTouches++
            if (entries.size <= capacity) return emptyList()
            val evicted = mutableListOf<String>()
            val iterator = entries.iterator()
            while (entries.size > capacity.coerceAtLeast(0) && iterator.hasNext()) {
                evicted.add(iterator.next())
                iterator.remove()
            }
            if (entries.isEmpty()) newest = null
            return evicted
        }

        fun remove(key: String) {
            entries.remove(key)
            if (newest == key) newest = entries.lastOrNull()
        }

        fun encode(): String = entries.joinToString(",")
    }

    private fun previewImageUrlKey(entryId: String): String =
        PreviewCachePolicy.PREVIEW_IMAGE_URL_PREFIX + entryId

    private fun previewImageLoadedKey(entryId: String): String =
        PreviewCachePolicy.PREVIEW_IMAGE_LOADED_PREFIX + entryId

    private fun previewImageMissTimeKey(entryId: String): String =
        PreviewCachePolicy.PREVIEW_IMAGE_MISS_TIME_PREFIX + entryId

    private fun linkSummaryKey(normalizedUrl: String): String =
        PreviewCachePolicy.LINK_SUMMARY_PREFIX + stableHash(normalizedUrl)

}
