package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StoryResourceTintStore
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.network.StoryResourceTintKind
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.presentation.StoryListResourceRuntime
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.StoryPreviewTintState

data class StoryHeaderTintPresentation(
    val previewImageUrl: String?,
    val faviconUrl: String?,
    val paletteMode: String,
    val initialTintArgb: Int?,
)

/** Shared tint/source resolution used by every comments-header host. */
fun storyHeaderTintPresentation(
    story: Story,
    previewResource: StoryPreviewResourceState?,
    faviconProvider: String,
    paletteTintMode: String,
    tintBaseColor: Int,
    tintStore: StoryResourceTintStore,
): StoryHeaderTintPresentation {
    val paletteMode = PaletteTintPreferences.normalizeConfigKey(paletteTintMode)
    val faviconUrl = runCatching {
        FaviconUrlBuilder.faviconUrl(story.url.orEmpty(), faviconProvider)
    }.getOrNull()
    val previewImageUrl = previewResource?.imageUrl ?: story.previewImageUrl
    val persistedPreviewTint = previewImageUrl?.let { sourceUrl ->
        tintStore.read(
            story.id,
            StoryResourceTintKind.PREVIEW_IMAGE,
            sourceUrl,
            tintBaseColor,
            StoryPreviewTintState.storedMode(paletteMode),
        )?.tintColorArgb
    }
    val resourceTint = previewResource?.previewTint
    val initialTint = when {
        resourceTint != null && resourceTint.sourceUrl == previewImageUrl &&
            resourceTint.baseColorArgb == tintBaseColor &&
            StoryPreviewTintState.isModeCurrent(resourceTint.paletteConfigKey, paletteMode) ->
            resourceTint.tintColorArgb
        persistedPreviewTint != null -> persistedPreviewTint
        StoryPreviewTintState.isPreviewCurrent(story, tintBaseColor, paletteMode) ->
            story.previewImageTintColor
        story.faviconTintColorLoaded && story.faviconTintBaseColor == tintBaseColor &&
            StoryPreviewTintState.isModeCurrent(story.faviconTintMode, paletteMode) &&
            story.faviconTintSourceUrl == faviconUrl -> story.faviconTintColor
        else -> null
    }
    return StoryHeaderTintPresentation(
        previewImageUrl = previewImageUrl,
        faviconUrl = faviconUrl,
        paletteMode = paletteMode,
        initialTintArgb = initialTint,
    )
}

/** Canonical KMP story-row mapping shared by stories, comments headers, and submissions. */
fun storyItemUiModel(
    story: Story,
    position: Int,
    settings: StoryDisplaySettings,
    previewResource: StoryPreviewResourceState?,
    tintBaseColor: Int,
    tintStore: StoryResourceTintStore,
): StoryItemUiModel {
    val favicon = runCatching {
        FaviconUrlBuilder.faviconUrl(story.url.orEmpty(), settings.faviconProvider)
    }.getOrNull()
    val paletteMode = PaletteTintPreferences.normalizeConfigKey(settings.paletteTintMode)
    val previewUrl = previewResource?.imageUrl ?: story.previewImageUrl
    val currentPreviewTint = StoryPreviewTintState.isPreviewCurrent(
        story,
        tintBaseColor,
        paletteMode,
    )
    val currentFaviconTint = story.faviconTintColorLoaded &&
        story.faviconTintBaseColor == tintBaseColor &&
        StoryPreviewTintState.isModeCurrent(story.faviconTintMode, paletteMode) &&
        story.faviconTintSourceUrl == favicon
    val persistedPreviewTint = previewUrl?.let { sourceUrl ->
        tintStore.read(
            story.id,
            StoryResourceTintKind.PREVIEW_IMAGE,
            sourceUrl,
            tintBaseColor,
            StoryPreviewTintState.storedMode(paletteMode),
        )?.tintColorArgb
    }
    val persistedFaviconTint = favicon?.let { sourceUrl ->
        tintStore.read(
            story.id,
            StoryResourceTintKind.FAVICON,
            sourceUrl,
            tintBaseColor,
            StoryPreviewTintState.storedMode(paletteMode),
        )?.tintColorArgb
    }
    return StoryItemUiModelFactory.create(
        story = story,
        position = position,
        resources = StoryItemResourcePresentation(
            faviconUrl = favicon,
            summary = story.linkSummaryDescription,
            previewImageUrl = previewUrl,
            previewImageLoadFailed = story.previewImageLoadFailed,
            faviconTintArgb = persistedFaviconTint
                ?: story.faviconTintColor.takeIf { currentFaviconTint },
            previewImageTintArgb = persistedPreviewTint
                ?: story.previewImageTintColor.takeIf { currentPreviewTint },
            tintFallbackArgb = tintBaseColor,
        ).withPreviewResource(previewResource, paletteMode),
    )
}

@Composable
fun rememberSubmissionStoryItemUiModel(
    story: Story,
    settings: StoryDisplaySettings,
    previewState: StoryPreviewResourceState?,
    previewResources: StoryListResourceRuntime,
    tintBaseColor: Int,
): StoryItemUiModel {
    DisposableEffect(
        previewResources,
        story.id,
        story.url,
        settings.previewImageMode,
        settings.showSummary,
    ) {
        previewResources.request(story)
        onDispose { }
    }
    val currentPreviewState = previewState?.takeIf { it.pageUrl == story.url }
    val previewUrl = currentPreviewState?.imageUrl ?: story.previewImageUrl
    val summary = currentPreviewState?.summary?.description
        ?: story.linkSummaryDescription
        ?: story.summary.orEmpty()
    val faviconUrl = remember(story.url, settings.faviconProvider) {
        runCatching {
            FaviconUrlBuilder.faviconUrl(story.url.orEmpty(), settings.faviconProvider)
        }.getOrNull()
    }
    return StoryItemUiModelFactory.create(
        story = story,
        resources = StoryItemResourcePresentation(
            summary = summary,
            faviconUrl = faviconUrl,
            previewImageUrl = previewUrl,
            previewImageLoadFailed = currentPreviewState?.imageLoadFailed
                ?: story.previewImageLoadFailed,
            faviconTintArgb = previewResources.tintFor(
                story,
                StoryResourceTintKind.FAVICON,
                faviconUrl,
                tintBaseColor,
                settings.paletteTintMode,
            ),
            previewImageTintArgb = previewResources.tintFor(
                story,
                StoryResourceTintKind.PREVIEW_IMAGE,
                previewUrl,
                tintBaseColor,
                settings.paletteTintMode,
            ),
            tintFallbackArgb = tintBaseColor,
        ).withPreviewResource(currentPreviewState, settings.paletteTintMode),
        loadingTitle = "",
        failedTitle = "",
    )
}
