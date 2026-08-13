package com.simon.harmonichackernews.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PreviewContent(
    val imageUrl: String?,
    val summary: LinkSummary?,
)

/** Coalesces preview requests and owns platform-neutral memory hit/miss policy. */
class PreviewContentCoordinator(
    private val scope: CoroutineScope,
    private val maxImageEntries: Int = 300,
    private val maxMissEntries: Int = 1_000,
) {
    private val mutex = Mutex()
    private val images = mutableMapOf<String, String>()
    private val summaries = mutableMapOf<String, LinkSummary>()
    private val misses = linkedSetOf<String>()
    private val pending = mutableMapOf<String, Deferred<PreviewContent>>()

    suspend fun load(
        pageUrl: String,
        requireSummary: Boolean,
        forceRefresh: Boolean,
        fetch: suspend () -> LinkSummary,
    ): PreviewContent {
        val existing = mutex.withLock {
            if (!forceRefresh) {
                summaries[pageUrl]?.let { summary ->
                    if (requireSummary) return@withLock Existing.Content(
                        PreviewContent(summary.imageUrl.ifEmpty { images[pageUrl] }, summary)
                    )
                }
                if (!requireSummary) {
                    images[pageUrl]?.let { return@withLock Existing.Content(PreviewContent(it, null)) }
                    if (pageUrl in misses) return@withLock Existing.Content(PreviewContent(null, null))
                }
            }
            pending[pageUrl]?.let { return@withLock Existing.Pending(it) }
            val request = scope.async {
                val summary = fetch()
                PreviewContent(summary.imageUrl.ifEmpty { null }, summary)
            }
            pending[pageUrl] = request
            request.invokeOnCompletion {
                scope.launch {
                    mutex.withLock {
                        if (pending[pageUrl] === request) pending.remove(pageUrl)
                    }
                }
            }
            Existing.Pending(request)
        }
        if (existing is Existing.Content) return existing.value

        val request = (existing as Existing.Pending).request
        val content = try {
            request.await()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            PreviewContent(null, null)
        }
        mutex.withLock {
            if (pending[pageUrl] === request) pending.remove(pageUrl)
            content.summary?.let {
                if (summaries.size >= maxImageEntries) summaries.clear()
                summaries[pageUrl] = it
            }
            if (content.imageUrl.isNullOrEmpty()) {
                misses.remove(pageUrl)
                misses.add(pageUrl)
                while (misses.size > maxMissEntries) misses.remove(misses.first())
            } else {
                if (images.size >= maxImageEntries) {
                    images.clear()
                    misses.clear()
                }
                images[pageUrl] = content.imageUrl
            }
        }
        return content
    }

    private sealed interface Existing {
        data class Content(val value: PreviewContent) : Existing
        data class Pending(val request: Deferred<PreviewContent>) : Existing
    }
}

data class PreviewCacheOrderUpdate(
    val order: List<String>,
    val evicted: List<String>,
)

/** Stable cache identifiers and LRU ordering shared by every platform persistence adapter. */
object PreviewCachePolicy {
    const val STORE_NAME =
        "com.simon.harmonichackernews.PREVIEW_IMAGE_CACHE_PREFERENCES"
    const val MAX_DISK_ENTRIES = 1_000
    const val YOUTUBE_OEMBED_SUFFIX = "youtube_oembed"
    const val PREVIEW_IMAGE_ORDER_KEY =
        "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_CACHE_ORDER"
    const val LINK_SUMMARY_ORDER_KEY =
        "com.simon.harmonichackernews.KEY_LINK_SUMMARY_CACHE_ORDER"
    const val PREVIEW_IMAGE_URL_PREFIX =
        "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_URL"
    const val PREVIEW_IMAGE_LOADED_PREFIX =
        "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_URL_LOADED"
    const val LINK_SUMMARY_PREFIX = "com.simon.harmonichackernews.KEY_LINK_SUMMARY"

    fun previewEntryId(storyId: Int, pageUrl: String?): String? = when {
        storyId <= 0 -> null
        LinkSummaryParser.isYoutubeVideoUrl(pageUrl) -> "$storyId:$YOUTUBE_OEMBED_SUFFIX"
        else -> storyId.toString()
    }

    fun decodeOrder(serialized: String?): MutableList<String> {
        val seen = mutableSetOf<String>()
        return serialized.orEmpty().split(',')
            .filter { it.isNotEmpty() && seen.add(it) }
            .toMutableList()
    }

    fun encodeOrder(order: List<String>): String = order.joinToString(",")

    fun touch(
        currentOrder: List<String>,
        key: String,
        maxEntries: Int = MAX_DISK_ENTRIES,
    ): PreviewCacheOrderUpdate {
        val order = currentOrder.filterTo(mutableListOf()) { it != key }
        order.add(key)
        val evicted = mutableListOf<String>()
        while (order.size > maxEntries) evicted.add(order.removeAt(0))
        return PreviewCacheOrderUpdate(order, evicted)
    }

}
