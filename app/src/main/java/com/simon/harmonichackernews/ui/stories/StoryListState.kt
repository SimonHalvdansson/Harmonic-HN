package com.simon.harmonichackernews.ui.stories

import android.content.Context
import android.graphics.Color
import coil3.Image
import coil3.imageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.target.Target
import com.simon.harmonichackernews.adapters.StoryDisplaySettings
import com.simon.harmonichackernews.adapters.StoryDisplayState
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
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.utils.PreviewImageTintUtils
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils
import java.util.IdentityHashMap
import kotlin.math.min

/**
 * Non-View state owned by `StoriesCoordinator` while Compose renders the story list.
 *
 * This replaces the old unattached RecyclerView adapter. It deliberately contains only the
 * stateful responsibilities still needed by the Compose screen: pagination, display settings,
 * preview metadata/image prefetch, and card tint caching.
 */
class StoryListState(
    private val stories: MutableList<Story>,
    settings: StoryDisplaySettings,
    wantedType: Int,
) : StoryDisplayState {
    private val previewRequests: MutableMap<Story, PreviewImageRequest> = IdentityHashMap()
    private val imagePrefetches: MutableMap<Story, Disposable> = IdentityHashMap()
    private var changedListener: ((Story?) -> Unit)? = null

    override var showPoints: Boolean = false
    override var compactPoints: Boolean = false
    override var includeTopLevelDomain: Boolean = false
    override var showCommentsCount: Boolean = false
    override var compactView: Boolean = false
    override var thumbnails: Boolean = false
    override var previewImageMode: String = SettingsUtils.STORY_PREVIEW_IMAGE_OFF
    override var borderlessLargePreviewImage: Boolean = false
    override var showSummary: Boolean = false
    override var storyTextSize: Float = 0f
    override var showIndex: Boolean = false
    override var compactHeader: Boolean = false
    override var leftAlign: Boolean = false
    override var cardStyle: Boolean = false
    override var tintCardUsingPreview: Boolean = false
    override var paletteTintMode: String = ""
    override var faviconProvider: String = SettingsUtils.FAVICON_PROVIDER_GOOGLE
    override var hotness: Int = 0
    var type: Int
    override var font: String = "googlesansflexrounded"
    override var commentTextSize: Float = 0f
    var allowCommentRows: Boolean = false
    var disableClickedEffects: Boolean = false
    override var grayOutClicked: Boolean = false

    var paginationMode: Boolean = false
    var showLoadMoreButton: Boolean = false
    var visibleStoryCount: Int = PAGINATION_PAGE_SIZE
    private var loadMoreLoading = false

    init {
        applyInitialSettings(settings)
        type = wantedType
    }

    fun setChangedListener(listener: ((Story?) -> Unit)?) {
        changedListener = listener
    }

    val itemCount: Int
        get() = visibleStoryItemCount + if (hasLoadMoreButton()) 1 else 0

    val visibleStoryItemCount: Int
        get() = if (paginationMode) min(
            visibleStoryCount,
            stories.size,
        ) else stories.size

    fun hasLoadMoreButton(): Boolean =
        loadMoreLoading ||
            showLoadMoreButton ||
            (paginationMode && visibleStoryCount < stories.size)

    fun isLoadMoreLoading(): Boolean = loadMoreLoading

    fun setLoadMoreLoading(loading: Boolean) {
        if (loadMoreLoading == loading) return
        loadMoreLoading = loading
        notifyChanged()
    }

    fun loadNextPage() {
        val oldVisibleCount = visibleStoryCount
        visibleStoryCount = min(visibleStoryCount + PAGINATION_PAGE_SIZE, stories.size)
        if (visibleStoryCount != oldVisibleCount) notifyChanged()
    }

    fun updateStoryClickedState(position: Int) {
        stories.getOrNull(position)?.let(::notifyChanged)
    }

    fun updateStoryIndicesFromPosition(position: Int) {
        if (showIndex && position in 0..<visibleStoryItemCount) notifyChanged()
    }

    fun notifyDataSetChanged() {
        notifyChanged()
    }

    fun notifyItemChanged(position: Int) {
        notifyChanged(stories.getOrNull(position))
    }

    fun notifyItemInserted(position: Int) {
        notifyChanged()
    }

    fun notifyItemRemoved(position: Int) {
        notifyChanged()
    }

    fun notifyItemRangeChanged(positionStart: Int, itemCount: Int) {
        notifyChanged()
    }

    fun notifyItemRangeInserted(positionStart: Int, itemCount: Int) {
        notifyChanged()
    }

    fun notifyItemRangeRemoved(positionStart: Int, itemCount: Int) {
        notifyChanged()
    }

    override fun invalidateTypography() {
        // Compose typography is derived from StoryDisplaySettings on recomposition.
    }

    fun resolveStoryCardBackgroundColor(context: Context?, story: Story?): Int {
        val baseColor = context?.let(PreviewImageTintUtils::getTintBaseColor) ?: Color.TRANSPARENT
        if (!tintCardUsingPreview || story == null) return baseColor
        if (previewImageMode != SettingsUtils.STORY_PREVIEW_IMAGE_OFF &&
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

        val previewEnabled = SettingsUtils.STORY_PREVIEW_IMAGE_OFF != previewImageMode
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
                    notifyChanged(story)
                })
        previewRequests[story] = request
    }

    fun dispose() {
        changedListener = null
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

    private fun applyInitialSettings(settings: StoryDisplaySettings) {
        showPoints = settings.showPoints
        compactPoints = settings.compactPoints
        includeTopLevelDomain = settings.includeTopLevelDomain
        showCommentsCount = settings.showCommentsCount
        compactView = settings.compactView
        thumbnails = settings.thumbnails
        previewImageMode = settings.previewImageMode
        borderlessLargePreviewImage = settings.borderlessLargePreviewImage
        showSummary = settings.showSummary
        storyTextSize = settings.storyTextSize
        showIndex = settings.showIndex
        compactHeader = settings.compactHeader
        leftAlign = settings.leftAlign
        cardStyle = settings.cardStyle
        tintCardUsingPreview = settings.tintCardUsingPreview
        paletteTintMode = PaletteTintPreferences.normalizeConfigKey(settings.paletteTintMode)
        grayOutClicked = settings.grayOutClicked
        hotness = settings.hotness
        faviconProvider = settings.faviconProvider
        font = settings.font
        commentTextSize = settings.commentTextSize
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
        val width = if (SettingsUtils.STORY_PREVIEW_IMAGE_LARGE == previewImageMode)
            context.resources.displayMetrics.widthPixels
        else
            Utils.pxFromDpInt(context.resources, 72f)
        val height = if (SettingsUtils.STORY_PREVIEW_IMAGE_LARGE == previewImageMode)
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
                        notifyChanged(story)
                    }
                }

                override fun onSuccess(result: Image) {
                    imagePrefetches.remove(story)
                    story.previewImageLoading = false
                    story.previewImageLoaded = true
                    story.previewImageLoadFailed = false
                    cachePreviewState(context, story)
                    notifyChanged(story)
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

    private fun notifyChanged(story: Story? = null) = changedListener?.invoke(story)

    companion object {
        const val PAGINATION_PAGE_SIZE = 30
        private const val LARGE_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP = 176
    }
}
