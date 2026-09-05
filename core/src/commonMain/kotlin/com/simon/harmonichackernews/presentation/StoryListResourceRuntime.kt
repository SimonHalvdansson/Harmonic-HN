package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StoryResourceTintStore
import com.simon.harmonichackernews.data.canonicalize
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.network.LinkSummary
import com.simon.harmonichackernews.network.StoryPreviewResourceRequest
import com.simon.harmonichackernews.network.StoryPreviewResourceRuntime
import com.simon.harmonichackernews.network.StoryPreviewResourceService
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.network.resolvedImageUrl
import com.simon.harmonichackernews.network.StoryResourceTintKind
import com.simon.harmonichackernews.network.StoryResourceTintState
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.settings.StoryPreviewTintState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Portable owner of story preview content, viewport prefetch policy and resource presentation.
 *
 * Coil loading and palette extraction are performed by shared Compose UI. This runtime receives
 * those typed results and owns the resulting state; a platform only supplies the cache/network
 * implementation of [StoryPreviewResourceService].
 */
class StoryListResourceRuntime(
    scope: CoroutineScope,
    service: StoryPreviewResourceService,
    settings: StoryDisplaySettings,
    private val tintStore: StoryResourceTintStore = StoryResourceTintStore.None,
) {
    private val resourceRuntime = StoryPreviewResourceRuntime(scope, service)
    private val prefetchRuntime = PreviewPrefetchRuntime(
        scope = scope,
        batchSize = PREVIEW_RAMP_BATCH_SIZE,
        visibleThreshold = VISIBLE_PREFETCH_THRESHOLD,
        batchDelayMillis = PREVIEW_RAMP_DELAY_MS,
        onPrefetch = ::request,
    )

    private var resourceChangedListener: (() -> Unit)? = null

    var settings: StoryDisplaySettings = settings
        private set

    init {
        scope.launch {
            resourceRuntime.states.collect { resourceChangedListener?.invoke() }
        }
    }

    fun updateSettings(settings: StoryDisplaySettings): StoryDisplaySettings.UpdateResult =
        settings.changesFrom(this.settings).also { this.settings = settings }

    fun setResourceChangedListener(listener: (() -> Unit)?) {
        resourceChangedListener = listener
    }

    fun states(): Map<Int, StoryPreviewResourceState> = resourceRuntime.states.value

    val statesFlow: StateFlow<Map<Int, StoryPreviewResourceState>>
        get() = resourceRuntime.states

    fun stateFor(storyId: Int): StoryPreviewResourceState? = resourceRuntime.stateFor(storyId)

    fun request(story: Story?) {
        val previewEnabled = settings.previewImageMode != StoryPreviewMode.OFF
        if (!previewEnabled && !settings.showSummary) return
        requestCompletePreview(story)
    }

    /** Dialog previews always include every enrichment, independently of list display settings. */
    fun requestForDialog(story: Story?) {
        requestCompletePreview(story)
    }

    private fun requestCompletePreview(story: Story?) {
        if (story == null || !story.loaded || story.loadingFailed || story.isComment ||
            story.url.isNullOrEmpty()
        ) {
            return
        }
        val knownSummary = story.linkSummaryDescription
            ?.takeIf { story.linkSummaryLoaded }
            ?.let { LinkSummary(description = it, imageUrl = story.previewImageUrl.orEmpty()) }
        resourceRuntime.request(
            StoryPreviewResourceRequest(
                storyId = story.id,
                pageUrl = story.url.orEmpty(),
                // Ksoup produces both values from the same document. Always retain both so a
                // later dialog or settings change can reuse the parse without another request.
                loadImage = true,
                loadSummary = true,
                knownImageUrl = story.previewImageUrl,
                imageUrlAlreadyResolved = story.previewImageUrlLoaded,
                knownSummary = knownSummary,
            ),
        )
    }

    fun prefetchStory(story: Story?, stories: List<Story>) {
        if (story == null) return
        prefetchRuntime.enqueue(story, stories)
    }

    fun prefetchNearViewport(
        stories: List<Story>,
        initialLoadCount: Int,
        firstVisibleItem: Int = -1,
        lastVisibleItem: Int = -1,
        paginationVisibleCount: Int? = null,
    ) {
        if (stories.isEmpty() ||
            settings.previewImageMode == StoryPreviewMode.OFF && !settings.showSummary
        ) {
            return
        }
        prefetchRuntime.prefetchNearViewport(
            stories = stories,
            initialLoadCount = initialLoadCount,
            firstVisibleItem = firstVisibleItem,
            lastVisibleItem = lastVisibleItem,
            paginationVisibleCount = paginationVisibleCount,
        )
    }

    fun completePreviewImageLoad(
        storyId: Int,
        pageUrl: String,
        imageUrl: String,
        success: Boolean,
    ) {
        resourceRuntime.completeImageLoad(storyId, pageUrl, imageUrl, success)
    }

    fun recordTint(
        story: Story,
        kind: StoryResourceTintKind,
        sourceUrl: String,
        baseColorArgb: Int,
        paletteConfigKey: String,
        tintColorArgb: Int,
    ): Boolean {
        val pageUrl = story.url.orEmpty()
        val candidate = StoryResourceTintState(
            sourceUrl = sourceUrl,
            baseColorArgb = baseColorArgb,
            paletteConfigKey = StoryPreviewTintState.storedMode(paletteConfigKey),
            tintColorArgb = tintColorArgb,
        )
        if (!resourceRuntime.recordTint(story.id, pageUrl, kind, candidate)) return false
        val tint = tintStore.canonicalize(story.id, kind, candidate)
        if (tint != candidate) resourceRuntime.recordTint(story.id, pageUrl, kind, tint)
        return true
    }

    fun tintFor(
        story: Story,
        kind: StoryResourceTintKind,
        sourceUrl: String?,
        baseColorArgb: Int,
        paletteConfigKey: String,
    ): Int? {
        if (sourceUrl.isNullOrEmpty()) return null
        val storedPalette = StoryPreviewTintState.storedMode(paletteConfigKey)
        val stateTint = when (kind) {
            StoryResourceTintKind.PREVIEW_IMAGE -> stateFor(story.id)?.previewTint
            StoryResourceTintKind.FAVICON -> stateFor(story.id)?.faviconTint
        }
        stateTint?.takeIf {
            it.sourceUrl == sourceUrl &&
                it.baseColorArgb == baseColorArgb &&
                it.paletteConfigKey == storedPalette
        }?.let { return it.tintColorArgb }

        tintStore.read(
            storyId = story.id,
            kind = kind,
            sourceUrl = sourceUrl,
            baseColorArgb = baseColorArgb,
            paletteConfigKey = storedPalette,
        )?.let { return it.tintColorArgb }

        return when (kind) {
            StoryResourceTintKind.PREVIEW_IMAGE -> story.previewImageTintColor.takeIf {
                story.previewImageUrl == sourceUrl &&
                    StoryPreviewTintState.isPreviewCurrent(story, baseColorArgb, storedPalette)
            }
            StoryResourceTintKind.FAVICON -> story.faviconTintColor.takeIf {
                StoryPreviewTintState.isFaviconCurrent(
                    story,
                    sourceUrl,
                    baseColorArgb,
                    storedPalette,
                )
            }
        }
    }

    fun resolveCardBackgroundColor(
        story: Story?,
        baseColor: Int,
        previewState: StoryPreviewResourceState? = story?.let { stateFor(it.id) },
    ): Int {
        if (!settings.tintCardUsingPreview || story == null) return baseColor
        val previewTint = tintFor(
            story,
            StoryResourceTintKind.PREVIEW_IMAGE,
            previewState.resolvedImageUrl(story.previewImageUrl),
            baseColor,
            settings.paletteTintMode,
        )
        val previewUrl = previewState.resolvedImageUrl(story.previewImageUrl)
        val previewFailed = previewState?.imageLoadFailed == true
        val previewAvailable = settings.previewImageMode != StoryPreviewMode.OFF &&
            !previewUrl.isNullOrEmpty() && !previewFailed
        if (previewAvailable) return previewTint ?: baseColor
        val faviconUrl = runCatching {
            FaviconUrlBuilder.faviconUrl(story.url.orEmpty(), settings.faviconProvider)
        }.getOrNull()
        return tintFor(
            story,
            StoryResourceTintKind.FAVICON,
            faviconUrl,
            baseColor,
            settings.paletteTintMode,
        ) ?: baseColor
    }

    fun resetPrefetches() = prefetchRuntime.reset()

    fun dispose() {
        resetPrefetches()
        resourceChangedListener = null
        resourceRuntime.dispose()
    }

    private companion object {
        const val VISIBLE_PREFETCH_THRESHOLD = 17
        const val PREVIEW_RAMP_BATCH_SIZE = 10
        const val PREVIEW_RAMP_DELAY_MS = 450L
    }
}
