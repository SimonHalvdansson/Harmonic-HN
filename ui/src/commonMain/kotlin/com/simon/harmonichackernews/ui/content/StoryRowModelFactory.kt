package com.simon.harmonichackernews.ui.content

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.ItemTimeFormatter
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.utils.DomainNamePolicy
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.network.resolvedImageUrl
import com.simon.harmonichackernews.settings.StoryPreviewTintState
import kotlin.time.Clock

data class StoryRowResourcePresentation(
    val previewText: String? = null,
    val faviconUrl: String? = null,
    val previewImageUrl: String? = null,
    val previewImageLoadFailed: Boolean = false,
    val faviconTintArgb: Int? = null,
    val previewImageTintArgb: Int? = null,
    val tintFallbackArgb: Int? = null,
)

fun StoryRowResourcePresentation.withPreviewResource(
    resource: StoryPreviewResourceState?,
    paletteConfigKey: String? = null,
    canonicalPreviewTint: Int? = null,
    canonicalFaviconTint: Int? = null,
): StoryRowResourcePresentation = if (resource == null) {
    this
} else {
    val currentPreviewTint = resource.previewTint?.takeIf { tint ->
        tint.sourceUrl == resource.imageUrl &&
            tint.baseColorArgb == tintFallbackArgb &&
            (paletteConfigKey == null ||
                tint.paletteConfigKey == StoryPreviewTintState.storedMode(paletteConfigKey))
    }
    val currentFaviconTint = resource.faviconTint?.takeIf { tint ->
        tint.sourceUrl == faviconUrl &&
            tint.baseColorArgb == tintFallbackArgb &&
            (paletteConfigKey == null ||
                tint.paletteConfigKey == StoryPreviewTintState.storedMode(paletteConfigKey))
    }
    copy(
        previewText = resource.summary?.description ?: previewText,
        previewImageUrl = resource.resolvedImageUrl(previewImageUrl),
        previewImageLoadFailed = resource.imageLoadFailed,
        previewImageTintArgb = canonicalPreviewTint ?: currentPreviewTint?.tintColorArgb ?: previewImageTintArgb,
        faviconTintArgb = canonicalFaviconTint ?: currentFaviconTint?.tintColorArgb ?: faviconTintArgb,
    )
}

/** Pure Story/resource-snapshot to shared row-model mapping used by every platform list. */
object StoryRowModelFactory {
    fun create(
        item: StoryListItemSnapshot,
        position: Int? = null,
        resources: StoryRowResourcePresentation = StoryRowResourcePresentation(),
        loadingTitle: String = "Loading…",
        failedTitle: String = "Tap to retry",
        resolvedDomain: String? = null,
        nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): StoryRowModel {
        val fullDomain = resolvedDomain
            ?: item.url?.let(DomainNamePolicy::fromUrl).orEmpty()
        return createModel(
            title = item.title,
            pdfTitle = item.pdfTitle,
            videoTitle = item.videoTitle,
            loadingFailed = item.loadingFailed,
            linkSummaryDescription = item.presentation.linkSummaryDescription,
            aiSummaryText = item.presentation.aiSummaryText,
            points = item.score,
            createdAtEpochSeconds = item.createdAtEpochSeconds,
            commentCount = item.descendantCount,
            fullDomain = fullDomain,
            position = position,
            resources = resources,
            loadingTitle = loadingTitle,
            failedTitle = failedTitle,
            nowMillis = nowMillis,
        )
    }

    fun create(
        story: Story,
        position: Int? = null,
        resources: StoryRowResourcePresentation = StoryRowResourcePresentation(),
        loadingTitle: String = "Loading…",
        failedTitle: String = "Tap to retry",
        resolvedDomain: String? = null,
        nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): StoryRowModel {
        val fullDomain = resolvedDomain
            ?: runCatching { story.getDisplayDomain(true) }.getOrNull().orEmpty()
        return createModel(
            title = story.title,
            pdfTitle = story.pdfTitle,
            videoTitle = story.videoTitle,
            loadingFailed = story.loadingFailed,
            linkSummaryDescription = story.linkSummaryDescription,
            aiSummaryText = story.aiSummaryText,
            points = story.score,
            createdAtEpochSeconds = story.createdAtEpochSeconds,
            commentCount = story.descendants,
            fullDomain = fullDomain,
            position = position,
            resources = resources,
            loadingTitle = loadingTitle,
            failedTitle = failedTitle,
            nowMillis = nowMillis,
        )
    }

    private fun createModel(
        title: String?,
        pdfTitle: String?,
        videoTitle: String?,
        loadingFailed: Boolean,
        linkSummaryDescription: String?,
        aiSummaryText: String?,
        points: Int,
        createdAtEpochSeconds: Int,
        commentCount: Int,
        fullDomain: String,
        position: Int?,
        resources: StoryRowResourcePresentation,
        loadingTitle: String,
        failedTitle: String,
        nowMillis: Long,
    ): StoryRowModel {
        val shortDomain = DomainNamePolicy.formatForDisplay(fullDomain, false) ?: fullDomain
        val titlePresentation = storyTitlePresentation(
            title = title,
            pdfTitle = pdfTitle,
            videoTitle = videoTitle,
        )
        return StoryRowModel(
            index = position?.let { "${it + 1}." }.orEmpty(),
            title = if (title == null) {
                if (loadingFailed) failedTitle else loadingTitle
            } else {
                titlePresentation.text
            },
            titleBadge = titlePresentation.badge,
            previewText = resources.previewText
                ?: linkSummaryDescription
                ?: aiSummaryText.orEmpty(),
            points = points,
            domain = fullDomain,
            domainWithoutTopLevel = shortDomain,
            age = ItemTimeFormatter.format(createdAtEpochSeconds, nowMillis),
            commentCount = commentCount,
            faviconUrl = resources.faviconUrl,
            previewImageUrl = resources.previewImageUrl,
            previewImageLoadFailed = resources.previewImageLoadFailed,
            faviconTintArgb = resources.faviconTintArgb,
            previewImageTintArgb = resources.previewImageTintArgb,
            tintFallbackArgb = resources.tintFallbackArgb,
        )
    }
}
