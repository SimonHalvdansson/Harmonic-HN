package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.KeyValueStore

data class CachedPreviewImageUrl(
    val loaded: Boolean,
    val imageUrl: String?,
)

/**
 * Platform-neutral preview metadata cache.
 *
 * A platform supplies its existing key-value store and stable hash implementation. The caller is
 * responsible for serializing access when the backing store can be reached from multiple threads.
 * This keeps persistence format, negative hits, LRU eviction and in-memory tint/summary state
 * identical on Android, iOS and desktop without imposing a filesystem implementation on common.
 */
class PreviewContentCache(
    private val stableHash: (String) -> String = StableHash::sha256Hex,
    private val maxDiskEntries: Int = PreviewCachePolicy.MAX_DISK_ENTRIES,
    private val maxSummaryEntries: Int = 300,
    private val maxMemoryTintEntries: Int = 48,
) {
    private val summaries = mutableMapOf<String, LinkSummary>()
    private val tintColors = linkedMapOf<String, Int>()
    private val cacheOrders = mutableMapOf<String, List<String>>()

    fun loadPreviewImage(
        store: KeyValueStore?,
        entryId: String?,
        updateCacheOrder: Boolean = true,
    ): CachedPreviewImageUrl {
        if (store == null || entryId.isNullOrEmpty()) return CachedPreviewImageUrl(false, null)
        val imageUrl = store.getString(previewImageUrlKey(entryId), null)
        val loaded = store.getBoolean(previewImageLoadedKey(entryId), false) ||
            !imageUrl.isNullOrEmpty()
        if (updateCacheOrder && loaded) touch(store, PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY, entryId)
        return CachedPreviewImageUrl(loaded, imageUrl)
    }

    fun savePreviewImage(store: KeyValueStore?, entryId: String?, imageUrl: String?) {
        if (store == null || entryId.isNullOrEmpty()) return
        val orderUpdate = orderUpdate(store, PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY, entryId)
        store.putBoolean(previewImageLoadedKey(entryId), true)
        if (imageUrl.isNullOrEmpty()) {
            store.remove(previewImageUrlKey(entryId))
        } else {
            store.putString(previewImageUrlKey(entryId), imageUrl)
        }
        orderUpdate.evicted.forEach { oldestId ->
            store.remove(previewImageUrlKey(oldestId))
            store.remove(previewImageLoadedKey(oldestId))
        }
        writeOrder(store, PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY, orderUpdate.order)
    }

    fun cacheTintColor(storyId: Int, imageUrl: String?, baseColor: Int, tintColor: Int) {
        val key = tintKey(storyId, imageUrl, baseColor) ?: return
        tintColors.remove(key)
        tintColors[key] = tintColor
        while (tintColors.size > maxMemoryTintEntries) tintColors.remove(tintColors.keys.first())
    }

    fun saveTintColor(
        store: KeyValueStore?,
        storyId: Int,
        imageUrl: String?,
        baseColor: Int,
        tintColor: Int,
    ) {
        val key = tintKey(storyId, imageUrl, baseColor) ?: return
        cacheTintColor(storyId, imageUrl, baseColor, tintColor)
        if (store == null) return

        val orderedKeys = readOrder(store, PreviewCachePolicy.PREVIEW_TINT_ORDER_KEY)
        var legacyKeysRemoved = 0
        val currentKeys = orderedKeys.filterTo(mutableListOf()) { orderedKey ->
            val keep = PreviewCachePolicy.isCurrentTintKey(orderedKey) ||
                legacyKeysRemoved >= LEGACY_TINT_CACHE_KEYS_REMOVED_PER_SAVE
            if (!keep) {
                store.remove(orderedKey)
                legacyKeysRemoved++
            }
            keep
        }
        val orderUpdate = PreviewCachePolicy.touch(currentKeys, key, maxDiskEntries)
        store.putInt(key, tintColor)
        orderUpdate.evicted.forEach(store::remove)
        writeOrder(store, PreviewCachePolicy.PREVIEW_TINT_ORDER_KEY, orderUpdate.order)
    }

    fun loadTintColor(
        store: KeyValueStore?,
        storyId: Int,
        imageUrl: String?,
        baseColor: Int,
    ): Int? {
        val key = tintKey(storyId, imageUrl, baseColor) ?: return null
        tintColors[key]?.let { tintColor ->
            tintColors.remove(key)
            tintColors[key] = tintColor
            return tintColor
        }
        if (store == null || !store.contains(key)) return null
        return store.getInt(key, baseColor).also { tintColor ->
            cacheTintColor(storyId, imageUrl, baseColor, tintColor)
            touch(store, PreviewCachePolicy.PREVIEW_TINT_ORDER_KEY, key)
        }
    }

    fun loadLinkSummary(store: KeyValueStore?, normalizedUrl: String?): LinkSummary? {
        if (normalizedUrl.isNullOrEmpty()) return null
        summaries[normalizedUrl]?.let { return it }
        if (store == null) return null
        val key = linkSummaryKey(normalizedUrl)
        val result = LinkSummaryCodec.decode(store.getString(key, null)) ?: return null
        summaries[normalizedUrl] = result
        touch(store, PreviewCachePolicy.LINK_SUMMARY_ORDER_KEY, key)
        return result
    }

    fun saveLinkSummary(
        store: KeyValueStore?,
        normalizedUrl: String?,
        summary: LinkSummary?,
    ) {
        if (normalizedUrl.isNullOrEmpty() || summary == null) return
        if (summaries.size >= maxSummaryEntries) summaries.clear()
        summaries[normalizedUrl] = summary
        if (store == null) return

        val key = linkSummaryKey(normalizedUrl)
        val orderUpdate = orderUpdate(store, PreviewCachePolicy.LINK_SUMMARY_ORDER_KEY, key)
        store.putString(key, LinkSummaryCodec.encode(summary))
        orderUpdate.evicted.forEach(store::remove)
        writeOrder(store, PreviewCachePolicy.LINK_SUMMARY_ORDER_KEY, orderUpdate.order)
    }

    fun tintColorCount(store: KeyValueStore?): Int =
        if (store == null) 0 else readOrder(store, PreviewCachePolicy.PREVIEW_TINT_ORDER_KEY).size

    /** Clears process memory and invalidates cached order snapshots after platform storage cleanup. */
    fun reset() {
        summaries.clear()
        tintColors.clear()
        cacheOrders.clear()
    }

    fun clearTintMemoryAndOrderSnapshot() {
        tintColors.clear()
        cacheOrders.remove(PreviewCachePolicy.PREVIEW_TINT_ORDER_KEY)
    }

    private fun touch(store: KeyValueStore, orderKey: String, key: String) {
        val update = orderUpdate(store, orderKey, key)
        writeOrder(store, orderKey, update.order)
    }

    private fun orderUpdate(
        store: KeyValueStore,
        orderKey: String,
        key: String,
    ): PreviewCacheOrderUpdate = PreviewCachePolicy.touch(
        readOrder(store, orderKey),
        key,
        maxDiskEntries,
    )

    private fun readOrder(store: KeyValueStore, orderKey: String): MutableList<String> {
        cacheOrders[orderKey]?.let { return it.toMutableList() }
        return PreviewCachePolicy.decodeOrder(store.getString(orderKey, "")).also {
            cacheOrders[orderKey] = it.toList()
        }
    }

    private fun writeOrder(store: KeyValueStore, orderKey: String, order: List<String>) {
        cacheOrders[orderKey] = order.toList()
        store.putString(orderKey, PreviewCachePolicy.encodeOrder(order))
    }

    private fun previewImageUrlKey(entryId: String): String =
        PreviewCachePolicy.PREVIEW_IMAGE_URL_PREFIX + entryId

    private fun previewImageLoadedKey(entryId: String): String =
        PreviewCachePolicy.PREVIEW_IMAGE_LOADED_PREFIX + entryId

    private fun linkSummaryKey(normalizedUrl: String): String =
        PreviewCachePolicy.LINK_SUMMARY_PREFIX + stableHash(normalizedUrl)

    private fun tintKey(storyId: Int, imageUrl: String?, baseColor: Int): String? {
        if (storyId <= 0 || imageUrl.isNullOrEmpty()) return null
        return PreviewCachePolicy.PREVIEW_TINT_PREFIX +
            "$storyId:$baseColor:${PreviewCachePolicy.TINT_VERSION}:${stableHash(imageUrl)}"
    }

    private companion object {
        const val LEGACY_TINT_CACHE_KEYS_REMOVED_PER_SAVE = 8
    }
}
