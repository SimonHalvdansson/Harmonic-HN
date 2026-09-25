package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    override val imageFailures: PreviewImageFailureCache = PreviewImageFailureCache(store),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : StoryPreviewResourceService {
    private val cacheMutex = Mutex()

    override fun peekCached(request: StoryPreviewResourceRequest): CachedStoryPreviewResource? {
        val normalizedUrl = LinkSummaryParser.normalizeHttpUrl(request.pageUrl) ?: return null
        val entryId = PreviewCachePolicy.previewEntryId(request.storyId, normalizedUrl)
        // No LRU updates or summary hydration on the synchronous presentation path.
        val image = cache.loadPreviewImage(store, entryId, updateCacheOrder = false)
        return CachedStoryPreviewResource(image.loaded, image.imageUrl, null)
    }

    override suspend fun readCached(
        request: StoryPreviewResourceRequest,
    ): CachedStoryPreviewResource = withContext(dispatcher) {
        val normalizedUrl = LinkSummaryParser.normalizeHttpUrl(request.pageUrl)
            ?: return@withContext CachedStoryPreviewResource(false, null, null)
        val entryId = PreviewCachePolicy.previewEntryId(request.storyId, normalizedUrl)
        cacheMutex.withLock {
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
        if (forceRefresh) {
            peekCached(StoryPreviewResourceRequest(storyId, pageUrl, true, false))
                ?.imageUrl?.let { imageFailures.record(it, success = true) }
        }
        return withContext(dispatcher) {
            val normalizedUrl = LinkSummaryParser.normalizeHttpUrl(pageUrl)
                ?: return@withContext PreviewContent(null, null)
            val entryId = PreviewCachePolicy.previewEntryId(storyId, normalizedUrl)

            if (forceRefresh) {
                cacheMutex.withLock { cache.invalidatePreviewImage(store, entryId) }
            }
            if (!forceRefresh) {
                cachedContent(entryId, normalizedUrl, requireSummary)?.let { return@withContext it }
            }
            if (!requireSummary && LinkSummaryParser.isLikelyImageUrl(normalizedUrl)) {
                cacheMutex.withLock { cache.savePreviewImage(store, entryId, normalizedUrl) }
                return@withContext PreviewContent(normalizedUrl, null)
            }

            val content = coordinator.load(
                pageUrl = normalizedUrl,
                requireSummary = requireSummary,
                forceRefresh = forceRefresh,
            ) {
                linkSummaries.load(normalizedUrl, fallbackTitle)
            }
            coordinator.persistIfCurrent(normalizedUrl, content) {
                cacheMutex.withLock {
                    if (content.imageResult == PreviewImageResult.CONFIRMED) {
                        cache.savePreviewImage(store, entryId, content.imageUrl)
                    }
                    content.summary?.let {
                        if (cache.loadLinkSummary(store, normalizedUrl) != it) {
                            cache.saveLinkSummary(store, normalizedUrl, it)
                        }
                    }
                }
            }
            content
        }
    }

    /** Shared by reference labels, overlays, and compatible feed/header preloads. */
    suspend fun loadLinkSummary(
        pageUrl: String,
        fallbackTitle: String? = null,
        forceRefresh: Boolean = false,
        resolvedSummary: LinkSummary? = null,
    ): LinkSummary {
        if (!forceRefresh && resolvedSummary != null && isValidSummary(pageUrl, resolvedSummary)) {
            return resolvedSummary
        }
        val content = load(
            storyId = 0,
            pageUrl = pageUrl,
            requireSummary = true,
            forceRefresh = forceRefresh,
            fallbackTitle = fallbackTitle,
        )
        return content.summary ?: throw (content.failure ?: IllegalStateException("The page could not be read"))
    }

    companion object {
        fun isValidSummary(url: String, summary: LinkSummary): Boolean =
            (LinkSummaryParser.buildXkcdApiUrl(url) == null ||
                summary.contentType == LinkSummaryParser.XKCD_COMIC_CONTENT_TYPE) &&
            (LinkSummaryParser.hackerNewsItemId(url) == null ||
                (summary.contentType == LinkSummaryParser.HACKER_NEWS_ITEM_CONTENT_TYPE &&
                    (LinkSummaryParser.isHackerNewsStory(summary) || summary.commentTextVersion >= 1)))
    }

    suspend fun cachedLinkSummary(pageUrl: String): LinkSummary? = withContext(dispatcher) {
        val normalizedUrl = LinkSummaryParser.normalizeHttpUrl(pageUrl) ?: return@withContext null
        cacheMutex.withLock { cache.loadLinkSummary(store, normalizedUrl) }
    }

    suspend fun saveLinkSummary(pageUrl: String, summary: LinkSummary): Unit = withContext(dispatcher) {
        val normalizedUrl = LinkSummaryParser.normalizeHttpUrl(pageUrl) ?: return@withContext
        cacheMutex.withLock { cache.saveLinkSummary(store, normalizedUrl, summary) }
    }

    suspend fun clear() {
        withContext(dispatcher) {
            coordinator.clear {
                cacheMutex.withLock {
                    store.clear()
                    cache.reset()
                }
            }
        }
        imageFailures.onStoreCleared()
    }

    private suspend fun cachedContent(
        entryId: String?,
        normalizedUrl: String,
        requireSummary: Boolean,
    ): PreviewContent? = cacheMutex.withLock {
        val image = cache.loadPreviewImage(store, entryId)
        val summary = if (requireSummary) cache.loadLinkSummary(store, normalizedUrl)
            ?.takeIf { isValidSummary(normalizedUrl, it) } else null
        when {
            summary != null && image.loaded && image.imageUrl == null -> PreviewContent(null, summary)
            summary != null -> PreviewContent(summary.imageUrl.ifEmpty { image.imageUrl }, summary)
            !requireSummary && image.loaded -> PreviewContent(image.imageUrl, null)
            else -> null
        }
    }
}
