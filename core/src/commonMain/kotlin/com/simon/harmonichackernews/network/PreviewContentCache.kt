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
    private val summaries = mutableMapOf<String, LinkSummary>()
    private val cacheOrders = mutableMapOf<String, List<String>>()

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
            if (!loaded) invalidatePreviewImage(store, entryId)
        }
        if (updateCacheOrder && loaded) touch(store, PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY, entryId)
        return CachedPreviewImageUrl(loaded, imageUrl)
    }

    fun savePreviewImage(store: KeyValueStore?, entryId: String?, imageUrl: String?) {
        if (store == null || entryId.isNullOrEmpty()) return
        val orderUpdate = orderUpdate(store, PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY, entryId)
        store.update {
            putBoolean(previewImageLoadedKey(entryId), true)
            if (imageUrl.isNullOrEmpty()) {
                remove(previewImageUrlKey(entryId))
                putLong(previewImageMissTimeKey(entryId), nowMillis())
            } else {
                putString(previewImageUrlKey(entryId), imageUrl)
                remove(previewImageMissTimeKey(entryId))
            }
            orderUpdate.evicted.forEach { oldestId ->
                remove(previewImageUrlKey(oldestId))
                remove(previewImageLoadedKey(oldestId))
                remove(previewImageMissTimeKey(oldestId))
            }
            putString(
                PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY,
                PreviewCachePolicy.encodeOrder(orderUpdate.order),
            )
        }
        cacheOrders[PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY] = orderUpdate.order.toList()
    }

    fun invalidatePreviewImage(store: KeyValueStore?, entryId: String?) {
        if (store == null || entryId.isNullOrEmpty()) return
        val order = readOrder(store, PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY)
            .filter { it != entryId }
        store.update {
            remove(previewImageUrlKey(entryId))
            remove(previewImageLoadedKey(entryId))
            remove(previewImageMissTimeKey(entryId))
            putString(
                PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY,
                PreviewCachePolicy.encodeOrder(order),
            )
        }
        cacheOrders[PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY] = order
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

    /** Clears process memory and invalidates cached order snapshots after platform storage cleanup. */
    fun reset() {
        summaries.clear()
        cacheOrders.clear()
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

    private fun previewImageMissTimeKey(entryId: String): String =
        PreviewCachePolicy.PREVIEW_IMAGE_MISS_TIME_PREFIX + entryId

    private fun linkSummaryKey(normalizedUrl: String): String =
        PreviewCachePolicy.LINK_SUMMARY_PREFIX + stableHash(normalizedUrl)

}
