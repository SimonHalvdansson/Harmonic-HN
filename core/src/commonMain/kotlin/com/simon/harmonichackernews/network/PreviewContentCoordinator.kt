package com.simon.harmonichackernews.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

data class PreviewContent(
    val imageUrl: String?,
    val summary: LinkSummary?,
    /** Whether an absent image is a parsed response rather than a transport/parser failure. */
    val imageResult: PreviewImageResult = PreviewImageResult.CONFIRMED,
)

enum class PreviewImageResult { CONFIRMED, TRANSIENT_FAILURE }

/** Coalesces preview requests and owns platform-neutral memory hit/miss policy. */
class PreviewContentCoordinator(
    private val scope: CoroutineScope,
    private val maxImageEntries: Int = 300,
    private val maxMissEntries: Int = 1_000,
    private val missTtlMillis: Long = PreviewCachePolicy.NEGATIVE_IMAGE_TTL_MILLIS,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutex = Mutex()
    private val images = mutableMapOf<String, String>()
    private val summaries = mutableMapOf<String, LinkSummary>()
    private val misses = linkedMapOf<String, Long>()
    private val pending = mutableMapOf<String, PendingRequest>()
    private val latestRequestGenerations = linkedMapOf<String, Long>()
    private var nextRequestGeneration = 0L

    suspend fun load(
        pageUrl: String,
        requireSummary: Boolean,
        forceRefresh: Boolean,
        fetch: suspend () -> LinkSummary,
    ): PreviewContent {
        val existing = mutex.withLock {
            if (forceRefresh) {
                images.remove(pageUrl)
                summaries.remove(pageUrl)
                misses.remove(pageUrl)
            } else {
                summaries[pageUrl]?.let { summary ->
                    if (requireSummary) return@withLock Existing.Content(
                        PreviewContent(summary.imageUrl.ifEmpty { images[pageUrl] }, summary)
                    )
                }
                if (!requireSummary) {
                    images[pageUrl]?.let { return@withLock Existing.Content(PreviewContent(it, null)) }
                    misses[pageUrl]?.let { cachedAt ->
                        val now = nowMillis()
                        if (now >= cachedAt && now - cachedAt <= missTtlMillis) {
                            return@withLock Existing.Content(PreviewContent(null, null))
                        }
                        misses.remove(pageUrl)
                    }
                }
                pending[pageUrl]?.let { return@withLock Existing.Pending(it) }
            }
            val request = scope.async {
                try {
                    val summary = fetch()
                    PreviewContent(summary.imageUrl.ifEmpty { null }, summary)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    PreviewContent(null, null, PreviewImageResult.TRANSIENT_FAILURE)
                }
            }
            val pendingRequest = PendingRequest(request, ++nextRequestGeneration)
            pending[pageUrl] = pendingRequest
            latestRequestGenerations.remove(pageUrl)
            latestRequestGenerations[pageUrl] = pendingRequest.generation
            while (latestRequestGenerations.size > maxImageEntries + maxMissEntries) {
                latestRequestGenerations.remove(latestRequestGenerations.keys.first())
            }
            request.invokeOnCompletion {
                scope.launch {
                    mutex.withLock {
                        if (pending[pageUrl]?.request === request) pending.remove(pageUrl)
                    }
                }
            }
            Existing.Pending(pendingRequest)
        }
        if (existing is Existing.Content) return existing.value

        val pendingRequest = (existing as Existing.Pending).value
        val request = pendingRequest.request
        val content = try {
            request.await()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            PreviewContent(null, null, PreviewImageResult.TRANSIENT_FAILURE)
        }
        mutex.withLock {
            if (pending[pageUrl]?.request === request) pending.remove(pageUrl)
            if (latestRequestGenerations[pageUrl] != pendingRequest.generation) return@withLock
            content.summary?.let {
                if (summaries.size >= maxImageEntries) summaries.clear()
                summaries[pageUrl] = it
            }
            if (content.imageResult == PreviewImageResult.TRANSIENT_FAILURE) {
                // A timeout or parser exception is retryable and must not become a negative hit.
            } else if (content.imageUrl.isNullOrEmpty()) {
                misses.remove(pageUrl)
                misses[pageUrl] = nowMillis()
                while (misses.size > maxMissEntries) misses.remove(misses.keys.first())
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
        data class Pending(val value: PendingRequest) : Existing
    }

    private data class PendingRequest(
        val request: Deferred<PreviewContent>,
        val generation: Long,
    )
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
    const val NEGATIVE_IMAGE_TTL_MILLIS = 6L * 60L * 60L * 1_000L
    const val YOUTUBE_OEMBED_SUFFIX = "youtube_oembed"
    const val PREVIEW_IMAGE_ORDER_KEY =
        "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_CACHE_ORDER"
    const val LINK_SUMMARY_ORDER_KEY =
        "com.simon.harmonichackernews.KEY_LINK_SUMMARY_CACHE_ORDER"
    const val PREVIEW_IMAGE_URL_PREFIX =
        "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_URL"
    const val PREVIEW_IMAGE_LOADED_PREFIX =
        "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_URL_LOADED"
    const val PREVIEW_IMAGE_MISS_TIME_PREFIX =
        "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_URL_MISS_TIME"
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
