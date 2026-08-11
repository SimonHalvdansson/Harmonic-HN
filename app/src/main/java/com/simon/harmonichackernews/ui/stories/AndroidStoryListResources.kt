package com.simon.harmonichackernews.ui.stories

import android.content.Context
import android.graphics.Color
import coil3.Image
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.target.Target
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.FaviconLoader.getFaviconUrl
import com.simon.harmonichackernews.network.LinkSummary
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.networkHeader
import com.simon.harmonichackernews.network.StoryPreviewImageLoader.PreviewContentCallback
import com.simon.harmonichackernews.network.StoryPreviewImageLoader.PreviewImageRequest
import com.simon.harmonichackernews.network.StoryPreviewImageLoader.getCachedLinkSummary
import com.simon.harmonichackernews.network.StoryPreviewImageLoader.getCachedPreviewImageUrl
import com.simon.harmonichackernews.network.StoryPreviewImageLoader.isCachedPreviewImageUrlLoaded
import com.simon.harmonichackernews.network.StoryPreviewImageLoader.loadCachedPreviewImageTintColor
import com.simon.harmonichackernews.network.StoryPreviewImageLoader.loadPreviewContent
import com.simon.harmonichackernews.network.StoryPreviewImageLoader.saveCachedPreviewImageTintColor
import com.simon.harmonichackernews.utils.PreviewImageTintUtils
import com.simon.harmonichackernews.settings.StoryPreviewPreferences
import com.simon.harmonichackernews.utils.Utils
import java.util.IdentityHashMap

/**
 * Android image and cache facilities for stories rendered by shared Compose UI.
 * Structural list, paging and selection state deliberately do not live here.
 */
class AndroidStoryListResources(
    settings: StoryDisplaySettings,
) {
    private val previewRequests: MutableMap<Story, PreviewImageRequest> = IdentityHashMap()
    private val imagePrefetches: MutableMap<Story, Disposable> = IdentityHashMap()
    private var storyResourceChangedListener: ((Story) -> Unit)? = null
    var settings: StoryDisplaySettings = settings
        private set

    private val previewImageMode get() = settings.previewImageMode
    private val showSummary get() = settings.showSummary
    private val tintCardUsingPreview get() = settings.tintCardUsingPreview
    private val paletteTintMode get() = settings.paletteTintMode
    private val faviconProvider get() = settings.faviconProvider

    fun updateSettings(settings: StoryDisplaySettings): StoryDisplaySettings.UpdateResult =
        settings.changesFrom(this.settings).also { this.settings = settings }

    fun setStoryResourceChangedListener(listener: ((Story) -> Unit)?) {
        storyResourceChangedListener = listener
    }

    fun resolveStoryCardBackgroundColor(context: Context?, story: Story?): Int {
        val baseColor = context?.let(PreviewImageTintUtils::getTintBaseColor) ?: Color.TRANSPARENT
        if (!tintCardUsingPreview || story == null) return baseColor
        if (previewImageMode != StoryPreviewPreferences.OFF &&
            !story.previewImageLoadFailed &&
            PreviewImageTintUtils.isStoryPreviewImageTintColorCurrent(
                story,
                baseColor,
                paletteTintMode,
            )
        ) {
            return story.previewImageTintColor
        }
        val faviconUrl = getFaviconUrl(story)
        if (story.faviconTintColorLoaded
            && story.faviconTintBaseColor == baseColor
            && story.faviconTintSourceUrl == faviconUrl
            && PreviewImageTintUtils.isTintModeCurrent(story.faviconTintMode, paletteTintMode)
        ) {
            return story.faviconTintColor
        }
        return baseColor
    }

    fun prefetchPreviewImage(context: Context?, story: Story?) {
        if (context == null || story == null || !story.loaded || story.loadingFailed
            || story.isComment || story.url.isNullOrEmpty()
        ) {
            return
        }
        // Keep the Activity's themed context for color resolution. Application context resolves
        // the custom story-card attribute as transparent, which made otherwise-correct worker
        // results incompatible with the colors read by Compose.
        hydrateCachedPreviewState(context, story)

        val previewEnabled = StoryPreviewPreferences.OFF != previewImageMode
        if (!previewEnabled && !showSummary) return
        if (!story.previewImageUrl.isNullOrEmpty()) {
            if (previewEnabled) prefetchPreviewDrawable(context, story)
            return
        }
        if (previewRequests.containsKey(story)
            || story.previewImageUrlLoading
            || story.linkSummaryLoading
        ) {
            return
        }

        story.previewImageUrlLoading = previewEnabled
        story.linkSummaryLoading = showSummary
        val request =
            loadPreviewContent(
                context,
                story.id,
                story.url,
                showSummary,
                PreviewContentCallback { imageUrl: String?, summary: LinkSummary? ->
                    previewRequests.remove(story)
                    story.previewImageUrlLoading = false
                    story.linkSummaryLoading = false
                    story.previewImageUrlLoaded = true
                    if (!imageUrl.isNullOrEmpty()) {
                        setPreviewImageUrl(story, imageUrl)
                        story.previewImageLoadFailed = false
                    } else if (previewEnabled) {
                        story.previewImageLoadFailed = true
                        PreviewImageTintUtils.clearStoryPreviewImageTintColor(story)
                    }
                    if (showSummary) {
                        story.linkSummaryLoaded = true
                        story.linkSummaryDescription = summary?.description
                    }
                    cachePreviewState(context, story)
                    if (previewEnabled && !story.previewImageUrl.isNullOrEmpty()) {
                        prefetchPreviewDrawable(context, story)
                    }
                    publishStoryResourceChange(story)
                })
        previewRequests[story] = request
    }

    fun dispose() {
        storyResourceChangedListener = null
        for ((story, request) in previewRequests) {
            request.cancel()
            story.previewImageUrlLoading = false
            story.linkSummaryLoading = false
        }
        previewRequests.clear()
        for ((story, prefetch) in imagePrefetches) {
            prefetch.dispose()
            story.previewImageLoading = false
        }
        imagePrefetches.clear()
    }

    private fun hydrateCachedPreviewState(context: Context, story: Story) {
        if (story.previewImageUrl.isNullOrEmpty() && !story.previewImageUrlLoaded) {
            val loaded = isCachedPreviewImageUrlLoaded(
                context, story.id, story.url
            )
            if (loaded) {
                setPreviewImageUrl(
                    story, getCachedPreviewImageUrl(
                        context, story.id, story.url
                    )
                )
                story.previewImageUrlLoaded = true
                story.previewImageLoadFailed = story.previewImageUrl.isNullOrEmpty()
            }
        }
        if (showSummary && !story.linkSummaryLoaded) {
            getCachedLinkSummary(context, story.url)?.let { summary ->
                story.linkSummaryLoaded = true
                story.linkSummaryDescription = summary.description
                if (story.previewImageUrl.isNullOrEmpty()
                    && !summary.imageUrl.isNullOrEmpty()
                ) {
                    setPreviewImageUrl(story, summary.imageUrl)
                    story.previewImageUrlLoaded = true
                }
            }
        }
        if (tintCardUsingPreview && !story.previewImageUrl.isNullOrEmpty()) {
            val baseColor = PreviewImageTintUtils.getTintBaseColor(context)
            val tint = loadCachedPreviewImageTintColor(
                context, story.id, story.previewImageUrl, baseColor
            )
            if (tint != null) {
                PreviewImageTintUtils.applyCachedStoryPreviewImageTintColor(
                    story,
                    story.previewImageUrl,
                    baseColor,
                    paletteTintMode,
                    tint,
                )
            }
        }
    }

    private fun prefetchPreviewDrawable(context: Context, story: Story) {
        if (story.previewImageLoading
            || imagePrefetches.containsKey(story)
        ) {
            return
        }
        val imageUrl = story.previewImageUrl?.takeIf(String::isNotEmpty) ?: return
        if (story.previewImageLoaded) return

        story.previewImageLoading = true
        val width = if (StoryPreviewPreferences.LARGE == previewImageMode)
            context.resources.displayMetrics.widthPixels
        else
            Utils.pxFromDpInt(context.resources, 72f)
        val height = if (StoryPreviewPreferences.LARGE == previewImageMode)
            Utils.pxFromDpInt(
                context.resources,
                LARGE_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP.toFloat(),
            )
        else
            Utils.pxFromDpInt(context.resources, 54f)
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .networkHeader("User-Agent", NetworkComponent.USER_AGENT)
            .size(width, height)
            .target(object : Target {
                override fun onError(error: Image?) {
                    imagePrefetches.remove(story)
                    story.previewImageLoading = false
                    if (story.previewImageUrl == imageUrl) {
                        story.previewImageLoadFailed = true
                        PreviewImageTintUtils.clearStoryPreviewImageTintColor(story)
                        cachePreviewState(context, story)
                        publishStoryResourceChange(story)
                    }
                }

                override fun onSuccess(result: Image) {
                    imagePrefetches.remove(story)
                    story.previewImageLoading = false
                    story.previewImageLoaded = true
                    story.previewImageLoadFailed = false
                    cachePreviewState(context, story)
                    publishStoryResourceChange(story)
                }
            })
            .build()
        val disposable = context.imageLoader.enqueue(request)
        if (story.previewImageLoading) {
            imagePrefetches[story] = disposable
        }
    }

    private fun cachePreviewState(context: Context, story: Story) {
        Utils.cacheStoryPreviewState(context, story)
        val baseColor = PreviewImageTintUtils.getTintBaseColor(context)
        if (PreviewImageTintUtils.isStoryPreviewImageTintColorCurrent(
                story,
                baseColor,
                paletteTintMode,
            )
            && !story.previewImageTintSourceUrl.isNullOrEmpty()
        ) {
            saveCachedPreviewImageTintColor(
                context,
                story.id,
                story.previewImageTintSourceUrl,
                story.previewImageTintBaseColor,
                story.previewImageTintColor,
            )
        }
        if (story.faviconTintColorLoaded
            && story.faviconTintBaseColor == baseColor
            && PreviewImageTintUtils.isTintModeCurrent(
                story.faviconTintMode,
                paletteTintMode,
            )
            && !story.faviconTintSourceUrl.isNullOrEmpty()
        ) {
            saveCachedPreviewImageTintColor(
                context,
                story.id,
                story.faviconTintSourceUrl,
                story.faviconTintBaseColor,
                story.faviconTintColor,
            )
        }
    }

    private fun setPreviewImageUrl(story: Story, imageUrl: String?) {
        if (story.previewImageUrl != imageUrl) {
            PreviewImageTintUtils.clearStoryPreviewImageTintColor(story)
            story.previewImageLoaded = false
        }
        story.previewImageUrl = imageUrl
    }

    private fun getFaviconUrl(story: Story): String? =
        runCatching { getFaviconUrl(story.url, faviconProvider) }.getOrNull()

    private fun publishStoryResourceChange(story: Story) =
        storyResourceChangedListener?.invoke(story)

    companion object {
        private const val LARGE_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP = 176
    }
}
