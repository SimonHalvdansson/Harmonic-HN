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
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.presentation.StoryListResourceRuntime
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.StoryPreviewTintState
import com.simon.harmonichackernews.utils.DomainNamePolicy
import kotlin.time.Clock

data class StoryHeaderTintPresentation(
    val previewImageUrl: String?,
    val previewImageAvailable: Boolean,
    val faviconUrl: String?,
    val paletteMode: String,
    val initialTintArgb: Int?,
    val initialTintKind: StoryResourceTintKind?,
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
    val previewFailed = previewResource?.imageLoadFailed ?: story.previewImageLoadFailed
    val previewAvailable = !previewImageUrl.isNullOrBlank() && !previewFailed
    val persistedPreviewTint = previewImageUrl?.takeIf { previewAvailable }?.let { sourceUrl ->
        tintStore.read(
            story.id,
            StoryResourceTintKind.PREVIEW_IMAGE,
            sourceUrl,
            tintBaseColor,
            StoryPreviewTintState.storedMode(paletteMode),
        )?.tintColorArgb
    }
    val resourceTint = previewResource?.previewTint
    val previewTint = when {
        resourceTint != null && resourceTint.sourceUrl == previewImageUrl &&
            resourceTint.baseColorArgb == tintBaseColor &&
            StoryPreviewTintState.isModeCurrent(resourceTint.paletteConfigKey, paletteMode) ->
            resourceTint.tintColorArgb
        persistedPreviewTint != null -> persistedPreviewTint
        story.previewImageUrl == previewImageUrl &&
            StoryPreviewTintState.isPreviewCurrent(story, tintBaseColor, paletteMode) ->
            story.previewImageTintColor
        else -> null
    }
    val persistedFaviconTint = faviconUrl?.let { sourceUrl ->
        tintStore.read(
            story.id,
            StoryResourceTintKind.FAVICON,
            sourceUrl,
            tintBaseColor,
            StoryPreviewTintState.storedMode(paletteMode),
        )?.tintColorArgb
    }
    val faviconTint = persistedFaviconTint ?: story.faviconTintColor.takeIf {
        story.faviconTintColorLoaded && story.faviconTintBaseColor == tintBaseColor &&
            StoryPreviewTintState.isModeCurrent(story.faviconTintMode, paletteMode) &&
            story.faviconTintSourceUrl == faviconUrl
    }
    val initialTintKind = when {
        previewAvailable && previewTint != null -> StoryResourceTintKind.PREVIEW_IMAGE
        !previewAvailable && faviconTint != null -> StoryResourceTintKind.FAVICON
        else -> null
    }
    return StoryHeaderTintPresentation(
        previewImageUrl = previewImageUrl,
        previewImageAvailable = previewAvailable,
        faviconUrl = faviconUrl,
        paletteMode = paletteMode,
        initialTintArgb = when (initialTintKind) {
            StoryResourceTintKind.PREVIEW_IMAGE -> previewTint
            StoryResourceTintKind.FAVICON -> faviconTint
            null -> null
        },
        initialTintKind = initialTintKind,
    )
}

/** Immutable-story overload used by portable comments hosts. */
fun storyHeaderTintPresentation(
    story: StoryListItemSnapshot,
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
    val previewImageUrl = previewResource?.imageUrl ?: story.presentation.previewImage.url
    val previewFailed = previewResource?.imageLoadFailed ?: story.presentation.previewImage.failed
    val previewAvailable = !previewImageUrl.isNullOrBlank() && !previewFailed
    val persistedPreviewTint = previewImageUrl?.takeIf { previewAvailable }?.let { sourceUrl ->
        tintStore.read(
            story.id,
            StoryResourceTintKind.PREVIEW_IMAGE,
            sourceUrl,
            tintBaseColor,
            StoryPreviewTintState.storedMode(paletteMode),
        )?.tintColorArgb
    }
    val resourceTint = previewResource?.previewTint
    val snapshotPreviewTint = story.presentation.previewTint
    val previewTint = when {
        resourceTint != null && resourceTint.sourceUrl == previewImageUrl &&
            resourceTint.baseColorArgb == tintBaseColor &&
            StoryPreviewTintState.isModeCurrent(resourceTint.paletteConfigKey, paletteMode) ->
            resourceTint.tintColorArgb
        persistedPreviewTint != null -> persistedPreviewTint
        snapshotPreviewTint?.loaded == true && snapshotPreviewTint.sourceUrl == previewImageUrl &&
            snapshotPreviewTint.baseColorArgb == tintBaseColor &&
            StoryPreviewTintState.isModeCurrent(snapshotPreviewTint.mode, paletteMode) ->
            snapshotPreviewTint.colorArgb
        else -> null
    }
    val persistedFaviconTint = faviconUrl?.let { sourceUrl ->
        tintStore.read(
            story.id,
            StoryResourceTintKind.FAVICON,
            sourceUrl,
            tintBaseColor,
            StoryPreviewTintState.storedMode(paletteMode),
        )?.tintColorArgb
    }
    val snapshotFaviconTint = story.presentation.faviconTint
    val faviconTint = persistedFaviconTint ?: snapshotFaviconTint?.colorArgb?.takeIf {
        snapshotFaviconTint.loaded && snapshotFaviconTint.baseColorArgb == tintBaseColor &&
            StoryPreviewTintState.isModeCurrent(snapshotFaviconTint.mode, paletteMode) &&
            snapshotFaviconTint.sourceUrl == faviconUrl
    }
    val initialTintKind = when {
        previewAvailable && previewTint != null -> StoryResourceTintKind.PREVIEW_IMAGE
        !previewAvailable && faviconTint != null -> StoryResourceTintKind.FAVICON
        else -> null
    }
    return StoryHeaderTintPresentation(
        previewImageUrl = previewImageUrl,
        previewImageAvailable = previewAvailable,
        faviconUrl = faviconUrl,
        paletteMode = paletteMode,
        initialTintArgb = when (initialTintKind) {
            StoryResourceTintKind.PREVIEW_IMAGE -> previewTint
            StoryResourceTintKind.FAVICON -> faviconTint
            null -> null
        },
        initialTintKind = initialTintKind,
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
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
): StoryItemUiModel {
    val domain = runCatching { story.getDisplayDomain(true) }.getOrNull().orEmpty()
    val favicon = resolvedFaviconUrl(domain, story.url, settings.faviconProvider)
    val paletteMode = PaletteTintPreferences.normalizeConfigKey(settings.paletteTintMode)
    val previewUrl = previewResource?.imageUrl ?: story.previewImageUrl
    val currentPreviewTint = story.previewImageUrl == previewUrl &&
        StoryPreviewTintState.isPreviewCurrent(story, tintBaseColor, paletteMode)
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
        resolvedDomain = domain,
        nowMillis = nowMillis,
    )
}

/** Immutable story-row mapping used by the Stories feature boundary. */
fun storyItemUiModel(
    item: StoryListItemSnapshot,
    position: Int,
    settings: StoryDisplaySettings,
    previewResource: StoryPreviewResourceState?,
    tintBaseColor: Int,
    tintStore: StoryResourceTintStore,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
): StoryItemUiModel {
    val domain = item.url?.let(DomainNamePolicy::fromUrl).orEmpty()
    val favicon = resolvedFaviconUrl(domain, item.url, settings.faviconProvider)
    val paletteMode = PaletteTintPreferences.normalizeConfigKey(settings.paletteTintMode)
    val presentation = item.presentation
    val previewUrl = previewResource?.imageUrl ?: presentation.previewImage.url
    val previewTint = presentation.previewTint
    val faviconTint = presentation.faviconTint
    val currentPreviewTint = presentation.previewImage.url == previewUrl &&
        previewTint?.loaded == true && previewTint.baseColorArgb == tintBaseColor &&
        StoryPreviewTintState.isModeCurrent(previewTint.mode, paletteMode) &&
        previewTint.sourceUrl == previewUrl
    val currentFaviconTint = faviconTint?.loaded == true &&
        faviconTint.baseColorArgb == tintBaseColor &&
        StoryPreviewTintState.isModeCurrent(faviconTint.mode, paletteMode) &&
        faviconTint.sourceUrl == favicon
    val persistedPreviewTint = previewUrl?.let { sourceUrl ->
        tintStore.read(
            item.id,
            StoryResourceTintKind.PREVIEW_IMAGE,
            sourceUrl,
            tintBaseColor,
            StoryPreviewTintState.storedMode(paletteMode),
        )?.tintColorArgb
    }
    val persistedFaviconTint = favicon?.let { sourceUrl ->
        tintStore.read(
            item.id,
            StoryResourceTintKind.FAVICON,
            sourceUrl,
            tintBaseColor,
            StoryPreviewTintState.storedMode(paletteMode),
        )?.tintColorArgb
    }
    return StoryItemUiModelFactory.create(
        item = item,
        position = position,
        resources = StoryItemResourcePresentation(
            faviconUrl = favicon,
            summary = presentation.linkSummaryDescription,
            previewImageUrl = previewUrl,
            previewImageLoadFailed = presentation.previewImage.failed,
            faviconTintArgb = persistedFaviconTint
                ?: faviconTint?.colorArgb?.takeIf { currentFaviconTint },
            previewImageTintArgb = persistedPreviewTint
                ?: previewTint?.colorArgb?.takeIf { currentPreviewTint },
            tintFallbackArgb = tintBaseColor,
        ).withPreviewResource(previewResource, paletteMode),
        resolvedDomain = domain,
        nowMillis = nowMillis,
    )
}

private fun resolvedFaviconUrl(
    domain: String,
    pageUrl: String?,
    provider: String?,
): String? = runCatching {
    if (domain.isNotEmpty()) {
        FaviconUrlBuilder.faviconUrlForHost(domain, provider)
    } else {
        // Preserve the network parser's legacy handling for relative, missing, or unusual URLs.
        FaviconUrlBuilder.faviconUrl(pageUrl.orEmpty(), provider)
    }
}.getOrNull()

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
    val domainAndFavicon = remember(story.url, settings.faviconProvider) {
        val domain = runCatching { story.getDisplayDomain(true) }.getOrNull().orEmpty()
        domain to resolvedFaviconUrl(domain, story.url, settings.faviconProvider)
    }
    val domain = domainAndFavicon.first
    val faviconUrl = domainAndFavicon.second
    val nowMillis = remember(story.id, story.time) {
        Clock.System.now().toEpochMilliseconds()
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
        resolvedDomain = domain,
        nowMillis = nowMillis,
    )
}
