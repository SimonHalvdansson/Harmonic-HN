package com.simon.harmonichackernews.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StoryPreviewResourceRequest(
    val storyId: Int,
    val pageUrl: String,
    val loadImage: Boolean,
    val loadSummary: Boolean,
    val knownImageUrl: String? = null,
    val imageUrlAlreadyResolved: Boolean = knownImageUrl != null,
    val knownSummary: LinkSummary? = null,
)

data class CachedStoryPreviewResource(
    val imageUrlResolved: Boolean,
    val imageUrl: String?,
    val summary: LinkSummary?,
)

enum class StoryResourceTintKind {
    PREVIEW_IMAGE,
    FAVICON,
}

data class StoryResourceTintState(
    val sourceUrl: String,
    val baseColorArgb: Int,
    val paletteConfigKey: String,
    val tintColorArgb: Int,
)

data class StoryPreviewResourceState(
    val storyId: Int,
    val pageUrl: String,
    val loading: Boolean = false,
    val imageUrlResolved: Boolean = false,
    val imageUrl: String? = null,
    val summaryResolved: Boolean = false,
    val summary: LinkSummary? = null,
    val contentLoadFailed: Boolean = false,
    val imageLoading: Boolean = false,
    val imageLoaded: Boolean = false,
    val imageLoadFailed: Boolean = false,
    val previewTint: StoryResourceTintState? = null,
    val faviconTint: StoryResourceTintState? = null,
)

interface StoryPreviewResourceService {
    suspend fun readCached(request: StoryPreviewResourceRequest): CachedStoryPreviewResource
    suspend fun load(request: StoryPreviewResourceRequest): PreviewContent
}

/**
 * Owns preview-content request and cache-hydration state without mutating the shared Story model.
 * Platform shells may temporarily mirror snapshots back to legacy models while their UI adapters
 * migrate to reading [states] directly.
 */
class StoryPreviewResourceRuntime(
    private val scope: CoroutineScope,
    private val service: StoryPreviewResourceService,
) {
    private val mutableStates = MutableStateFlow<Map<Int, StoryPreviewResourceState>>(emptyMap())
    val states: StateFlow<Map<Int, StoryPreviewResourceState>> = mutableStates.asStateFlow()

    private val jobs = mutableMapOf<Int, Job>()
    private val activeRequests = mutableMapOf<Int, StoryPreviewResourceRequest>()

    fun stateFor(storyId: Int): StoryPreviewResourceState? = mutableStates.value[storyId]

    fun request(request: StoryPreviewResourceRequest): Boolean {
        if (request.storyId <= 0 || request.pageUrl.isBlank()) return false
        var effectiveRequest = request
        var current = stateFor(request.storyId)?.takeIf { it.pageUrl == request.pageUrl }
        current?.withKnown(request)?.let { reconciled ->
            if (reconciled != current) update(reconciled)
            current = reconciled
        }
        val activeJob = jobs[request.storyId]
        if (activeJob?.isActive == true) {
            val activeRequest = activeRequests[request.storyId]
            if (current != null && activeRequest?.covers(request) == true) return false
            effectiveRequest = request.mergedWith(activeRequest)
            activeJob.cancel()
            jobs.remove(request.storyId)
            activeRequests.remove(request.storyId)
            current = stateFor(request.storyId)?.takeIf {
                it.pageUrl == effectiveRequest.pageUrl
            }
        }
        if (current != null && current.satisfies(effectiveRequest) && !current.contentLoadFailed) {
            return false
        }

        val seeded = current ?: StoryPreviewResourceState(
            storyId = effectiveRequest.storyId,
            pageUrl = effectiveRequest.pageUrl,
            imageUrlResolved = effectiveRequest.imageUrlAlreadyResolved,
            imageUrl = effectiveRequest.knownImageUrl,
            summaryResolved = effectiveRequest.knownSummary != null,
            summary = effectiveRequest.knownSummary,
        )
        update(seeded.copy(loading = true, contentLoadFailed = false))
        val job = scope.launch {
            try {
                val cached = service.readCached(effectiveRequest)
                var next = (stateFor(effectiveRequest.storyId)
                    ?.takeIf { it.pageUrl == effectiveRequest.pageUrl }
                    ?: StoryPreviewResourceState(effectiveRequest.storyId, effectiveRequest.pageUrl))
                    .withCached(cached)
                update(next)

                if (!next.satisfies(effectiveRequest)) {
                    val loaded = service.load(effectiveRequest)
                    val resolvedImageUrl = loaded.imageUrl ?: next.imageUrl
                    val imageChanged = resolvedImageUrl != next.imageUrl
                    next = next.copy(
                        imageUrlResolved = next.imageUrlResolved || effectiveRequest.loadImage,
                        imageUrl = resolvedImageUrl,
                        summaryResolved = next.summaryResolved || effectiveRequest.loadSummary,
                        summary = loaded.summary ?: next.summary,
                        contentLoadFailed = false,
                        imageLoading = if (imageChanged) false else next.imageLoading,
                        imageLoaded = if (imageChanged) false else next.imageLoaded,
                        imageLoadFailed = if (imageChanged) false else next.imageLoadFailed,
                        previewTint = if (imageChanged) null else next.previewTint,
                    )
                }
                update(next.copy(loading = false))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                val failed = stateFor(effectiveRequest.storyId)
                    ?.takeIf { it.pageUrl == effectiveRequest.pageUrl }
                    ?: StoryPreviewResourceState(effectiveRequest.storyId, effectiveRequest.pageUrl)
                update(
                    failed.copy(
                        loading = false,
                        imageUrlResolved = failed.imageUrlResolved || effectiveRequest.loadImage,
                        summaryResolved = failed.summaryResolved || effectiveRequest.loadSummary,
                        contentLoadFailed = true,
                    ),
                )
            }
        }
        jobs[effectiveRequest.storyId] = job
        activeRequests[effectiveRequest.storyId] = effectiveRequest
        job.invokeOnCompletion {
            if (jobs[effectiveRequest.storyId] === job) {
                jobs.remove(effectiveRequest.storyId)
                activeRequests.remove(effectiveRequest.storyId)
            }
        }
        return true
    }

    fun beginImageLoad(storyId: Int, pageUrl: String, imageUrl: String): Boolean {
        val current = stateFor(storyId)?.takeIf {
            it.pageUrl == pageUrl && it.imageUrl == imageUrl
        } ?: return false
        if (current.imageLoading || current.imageLoaded || current.imageLoadFailed) return false
        update(
            current.copy(
                imageLoading = true,
                imageLoadFailed = false,
            ),
        )
        return true
    }

    fun completeImageLoad(
        storyId: Int,
        pageUrl: String,
        imageUrl: String,
        success: Boolean,
    ) {
        val current = stateFor(storyId)?.takeIf {
            it.pageUrl == pageUrl && it.imageUrl == imageUrl
        } ?: return
        update(
            current.copy(
                imageLoading = false,
                imageLoaded = success,
                imageLoadFailed = !success,
            ),
        )
    }

    fun recordTint(
        storyId: Int,
        pageUrl: String,
        kind: StoryResourceTintKind,
        tint: StoryResourceTintState,
    ): Boolean {
        val current = stateFor(storyId)?.takeIf { it.pageUrl == pageUrl }
            ?: StoryPreviewResourceState(storyId = storyId, pageUrl = pageUrl)
        if (kind == StoryResourceTintKind.PREVIEW_IMAGE && current.imageUrl != tint.sourceUrl) {
            return false
        }
        update(
            when (kind) {
                StoryResourceTintKind.PREVIEW_IMAGE -> current.copy(
                    previewTint = tint,
                    imageLoading = false,
                    imageLoaded = true,
                    imageLoadFailed = false,
                )
                StoryResourceTintKind.FAVICON -> current.copy(faviconTint = tint)
            },
        )
        return true
    }

    fun remove(storyId: Int) {
        jobs.remove(storyId)?.cancel()
        activeRequests.remove(storyId)
        if (storyId in mutableStates.value) mutableStates.value -= storyId
    }

    fun dispose() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        activeRequests.clear()
        mutableStates.value = emptyMap()
    }

    private fun StoryPreviewResourceState.satisfies(
        request: StoryPreviewResourceRequest,
    ): Boolean = (!request.loadImage || imageUrlResolved) &&
        (!request.loadSummary || summaryResolved)

    private fun StoryPreviewResourceState.withCached(
        cached: CachedStoryPreviewResource,
    ): StoryPreviewResourceState {
        val summaryImage = cached.summary?.imageUrl?.takeIf(String::isNotEmpty)
        val resolvedImageUrl = summaryImage ?: cached.imageUrl ?: imageUrl
        val imageChanged = resolvedImageUrl != imageUrl
        return copy(
            imageUrlResolved = imageUrlResolved || cached.imageUrlResolved || summaryImage != null,
            imageUrl = resolvedImageUrl,
            summaryResolved = summaryResolved || cached.summary != null,
            summary = cached.summary ?: summary,
            contentLoadFailed = false,
            imageLoading = if (imageChanged) false else imageLoading,
            imageLoaded = if (imageChanged) false else imageLoaded,
            imageLoadFailed = if (imageChanged) false else imageLoadFailed,
            previewTint = if (imageChanged) null else previewTint,
        )
    }

    private fun StoryPreviewResourceState.withKnown(
        request: StoryPreviewResourceRequest,
    ): StoryPreviewResourceState {
        val summaryImage = request.knownSummary?.imageUrl?.takeIf(String::isNotEmpty)
        val resolvedImageUrl = request.knownImageUrl ?: summaryImage ?: imageUrl
        val imageChanged = resolvedImageUrl != imageUrl
        return copy(
            imageUrlResolved = imageUrlResolved || request.imageUrlAlreadyResolved ||
                summaryImage != null,
            imageUrl = resolvedImageUrl,
            summaryResolved = summaryResolved || request.knownSummary != null,
            summary = request.knownSummary ?: summary,
            imageLoading = if (imageChanged) false else imageLoading,
            imageLoaded = if (imageChanged) false else imageLoaded,
            imageLoadFailed = if (imageChanged) false else imageLoadFailed,
            previewTint = if (imageChanged) null else previewTint,
        )
    }

    private fun update(state: StoryPreviewResourceState) {
        val currentStates = mutableStates.value
        if (currentStates[state.storyId] == state) return
        mutableStates.value = currentStates + (state.storyId to state)
    }

    private fun StoryPreviewResourceRequest.covers(
        other: StoryPreviewResourceRequest,
    ): Boolean = pageUrl == other.pageUrl &&
        (!other.loadImage || loadImage) &&
        (!other.loadSummary || loadSummary) &&
        (!other.imageUrlAlreadyResolved || imageUrlAlreadyResolved) &&
        (other.knownImageUrl == null || other.knownImageUrl == knownImageUrl) &&
        (other.knownSummary == null || other.knownSummary == knownSummary)

    private fun StoryPreviewResourceRequest.mergedWith(
        other: StoryPreviewResourceRequest?,
    ): StoryPreviewResourceRequest {
        if (other == null || pageUrl != other.pageUrl) return this
        return copy(
            loadImage = loadImage || other.loadImage,
            loadSummary = loadSummary || other.loadSummary,
            knownImageUrl = knownImageUrl ?: other.knownImageUrl,
            imageUrlAlreadyResolved = imageUrlAlreadyResolved || other.imageUrlAlreadyResolved,
            knownSummary = knownSummary ?: other.knownSummary,
        )
    }
}
