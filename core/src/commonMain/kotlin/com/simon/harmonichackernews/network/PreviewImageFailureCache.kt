package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

/**
 * Remembers failed image downloads independently of successfully parsed page metadata.
 * Reads are synchronous and do not write, so a screen can suppress an image on its first frame.
 * Like the presentation runtimes, result recording is confined to the UI dispatcher.
 */
class PreviewImageFailureCache(
    private val store: KeyValueStore,
    private val cooldownMillis: Long = 15L * 60L * 1_000L,
    private val maxEntries: Int = PreviewCachePolicy.MAX_DISK_ENTRIES,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutableChanges = MutableStateFlow(0L)
    val changes = mutableChanges.asStateFlow()

    fun isFailed(imageUrl: String): Boolean {
        val failedAt = store.getLong(key(imageUrl), 0L)
        val now = nowMillis()
        return failedAt > 0L && now >= failedAt && now - failedAt < cooldownMillis
    }

    fun record(imageUrl: String, success: Boolean) {
        if (imageUrl.isBlank()) return
        val key = key(imageUrl)
        if (success && !store.contains(key) || !success && isFailed(imageUrl)) return
        val order = PreviewCachePolicy.decodeOrder(store.getString(ORDER_KEY))
        val next = if (success) {
            PreviewCacheOrderUpdate(order.filter { it != key }, listOf(key))
        } else {
            PreviewCachePolicy.touch(order, key, maxEntries)
        }
        store.update {
            if (!success) putLong(key, nowMillis())
            next.evicted.forEach(::remove)
            putString(ORDER_KEY, PreviewCachePolicy.encodeOrder(next.order))
        }
        mutableChanges.value++
    }

    /** The owner has cleared the backing preview store. */
    fun onStoreCleared() {
        mutableChanges.value++
    }

    private fun key(imageUrl: String) = PREFIX + StableHash.sha256Hex(imageUrl)

    private companion object {
        const val PREFIX = "preview_image_failure."
        const val ORDER_KEY = "preview_image_failure_order"
    }
}
