package com.simon.harmonichackernews.ui.content

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.ItemTimeFormatter
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.utils.DomainNamePolicy
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.settings.StoryPreviewTintState

data class StoryItemResourcePresentation(
    val summary: String? = null,
    val faviconUrl: String? = null,
    val previewImageUrl: String? = null,
    val previewImageLoadFailed: Boolean = false,
    val faviconTintArgb: Int? = null,
    val previewImageTintArgb: Int? = null,
    val tintFallbackArgb: Int? = null,
)

fun StoryItemResourcePresentation.withPreviewResource(
    resource: StoryPreviewResourceState?,
    paletteConfigKey: String? = null,
): StoryItemResourcePresentation = if (resource == null) {
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
        summary = resource.summary?.description ?: summary,
        previewImageUrl = resource.imageUrl ?: previewImageUrl,
        previewImageLoadFailed = resource.imageLoadFailed,
        previewImageTintArgb = currentPreviewTint?.tintColorArgb ?: previewImageTintArgb,
        faviconTintArgb = currentFaviconTint?.tintColorArgb ?: faviconTintArgb,
    )
}

/** Pure Story/resource-snapshot to shared row-model mapping used by every platform list. */
object StoryItemUiModelFactory {
    fun create(
        item: StoryListItemSnapshot,
        position: Int? = null,
        resources: StoryItemResourcePresentation = StoryItemResourcePresentation(),
        loadingTitle: String = "Loading…",
        failedTitle: String = "Tap to retry",
    ): StoryItemUiModel {
        val fullDomain = item.url?.let(DomainNamePolicy::fromUrl).orEmpty()
        val shortDomain = DomainNamePolicy.formatForDisplay(fullDomain, false) ?: fullDomain
        val titlePresentation = storyTitlePresentation(
            title = item.title,
            pdfTitle = item.pdfTitle,
            videoTitle = item.videoTitle,
        )
        return StoryItemUiModel(
            index = position?.let { "${it + 1}." }.orEmpty(),
            title = if (item.title == null) {
                if (item.loadingFailed) failedTitle else loadingTitle
            } else {
                titlePresentation.text
            },
            titleBadge = titlePresentation.badge,
            summary = resources.summary
                ?: item.presentation.linkSummaryDescription
                ?: item.presentation.summary.orEmpty(),
            points = item.score,
            domain = fullDomain,
            domainWithoutTopLevel = shortDomain,
            age = ItemTimeFormatter.formatNow(item.createdAtEpochSeconds),
            commentCount = item.descendantCount,
            faviconUrl = resources.faviconUrl,
            previewImageUrl = resources.previewImageUrl,
            previewImageLoadFailed = resources.previewImageLoadFailed,
            faviconTintArgb = resources.faviconTintArgb,
            previewImageTintArgb = resources.previewImageTintArgb,
            tintFallbackArgb = resources.tintFallbackArgb,
        )
    }

    fun create(
        story: Story,
        position: Int? = null,
        resources: StoryItemResourcePresentation = StoryItemResourcePresentation(),
        loadingTitle: String = "Loading…",
        failedTitle: String = "Tap to retry",
    ): StoryItemUiModel {
        val fullDomain = runCatching { story.getDisplayDomain(true) }.getOrNull().orEmpty()
        val shortDomain = runCatching { story.getDisplayDomain(false) }.getOrNull() ?: fullDomain
        val titlePresentation = storyTitlePresentation(
            title = story.title,
            pdfTitle = story.pdfTitle,
            videoTitle = story.videoTitle,
        )
        return StoryItemUiModel(
            index = position?.let { "${it + 1}." }.orEmpty(),
            title = if (story.title == null) {
                if (story.loadingFailed) failedTitle else loadingTitle
            } else {
                titlePresentation.text
            },
            titleBadge = titlePresentation.badge,
            summary = resources.summary
                ?: story.linkSummaryDescription
                ?: story.summary.orEmpty(),
            points = story.score,
            domain = fullDomain,
            domainWithoutTopLevel = shortDomain,
            age = story.timeFormatted,
            commentCount = story.descendants,
            faviconUrl = resources.faviconUrl,
            previewImageUrl = resources.previewImageUrl,
            previewImageLoadFailed = resources.previewImageLoadFailed,
            faviconTintArgb = resources.faviconTintArgb,
            previewImageTintArgb = resources.previewImageTintArgb,
            tintFallbackArgb = resources.tintFallbackArgb,
        )
    }
}
