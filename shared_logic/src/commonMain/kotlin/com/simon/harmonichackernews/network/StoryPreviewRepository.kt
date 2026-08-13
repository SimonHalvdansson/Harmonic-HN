package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Application-scoped owner of preview loading and persistence.
 *
 * Hosts provide only a key-value store. URL policy, negative caching, request coalescing, link
 * summary persistence, and cache eviction remain identical on every platform.
 */
class StoryPreviewRepository(
    private val coordinator: PreviewContentCoordinator,
    private val linkSummaries: LinkSummaryRepository,
    private val store: KeyValueStore,
    private val cache: PreviewContentCache = PreviewContentCache(),
) : StoryPreviewResourceService {
    private val cacheMutex = Mutex()

    override suspend fun readCached(
        request: StoryPreviewResourceRequest,
    ): CachedStoryPreviewResource {
        val normalizedUrl = LinkSummaryParser.normalizeHttpUrl(request.pageUrl)
            ?: return CachedStoryPreviewResource(false, null, null)
        val entryId = PreviewCachePolicy.previewEntryId(request.storyId, normalizedUrl)
        return cacheMutex.withLock {
            val image = cache.loadPreviewImage(store, entryId)
            CachedStoryPreviewResource(
                imageUrlResolved = request.loadImage && image.loaded,
                imageUrl = image.imageUrl,
                summary = if (request.loadSummary) {
                    cache.loadLinkSummary(store, normalizedUrl)
                } else {
                    null
                },
            )
        }
    }

    override suspend fun load(request: StoryPreviewResourceRequest): PreviewContent = load(
        storyId = request.storyId,
        pageUrl = request.pageUrl,
        requireSummary = request.loadSummary,
    )

    suspend fun load(
        storyId: Int,
        pageUrl: String,
        requireSummary: Boolean,
        forceRefresh: Boolean = false,
        fallbackTitle: String? = null,
    ): PreviewContent {
        val normalizedUrl = LinkSummaryParser.normalizeHttpUrl(pageUrl)
            ?: return PreviewContent(null, null)
        val entryId = PreviewCachePolicy.previewEntryId(storyId, normalizedUrl)

        if (!forceRefresh) {
            cachedContent(entryId, normalizedUrl, requireSummary)?.let { return it }
        }
        if (!requireSummary && LinkSummaryParser.isLikelyImageUrl(normalizedUrl)) {
            cacheMutex.withLock { cache.savePreviewImage(store, entryId, normalizedUrl) }
            return PreviewContent(normalizedUrl, null)
        }

        val content = coordinator.load(
            pageUrl = normalizedUrl,
            requireSummary = requireSummary,
            forceRefresh = forceRefresh,
        ) {
            linkSummaries.load(normalizedUrl, fallbackTitle)
        }
        cacheMutex.withLock {
            cache.savePreviewImage(store, entryId, content.imageUrl)
            content.summary?.let { cache.saveLinkSummary(store, normalizedUrl, it) }
        }
        return content
    }

    suspend fun cachedLinkSummary(pageUrl: String): LinkSummary? {
        val normalizedUrl = LinkSummaryParser.normalizeHttpUrl(pageUrl) ?: return null
        return cacheMutex.withLock { cache.loadLinkSummary(store, normalizedUrl) }
    }

    suspend fun saveLinkSummary(pageUrl: String, summary: LinkSummary) {
        val normalizedUrl = LinkSummaryParser.normalizeHttpUrl(pageUrl) ?: return
        cacheMutex.withLock { cache.saveLinkSummary(store, normalizedUrl, summary) }
    }

    suspend fun clear() {
        cacheMutex.withLock {
            store.clear()
            cache.reset()
        }
    }

    private suspend fun cachedContent(
        entryId: String?,
        normalizedUrl: String,
        requireSummary: Boolean,
    ): PreviewContent? = cacheMutex.withLock {
        val image = cache.loadPreviewImage(store, entryId)
        val summary = if (requireSummary) cache.loadLinkSummary(store, normalizedUrl) else null
        when {
            summary != null -> PreviewContent(summary.imageUrl.ifEmpty { image.imageUrl }, summary)
            !requireSummary && image.loaded -> PreviewContent(image.imageUrl, null)
            else -> null
        }
    }
}
