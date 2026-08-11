package com.simon.harmonichackernews.ui.submissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.FaviconLoader
import com.simon.harmonichackernews.network.StoryPreviewImageLoader
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_public
import com.simon.harmonichackernews.settings.StoryPreviewPreferences
import com.simon.harmonichackernews.ui.content.StoryItemUiModel
import com.simon.harmonichackernews.utils.PreviewImageTintUtils
import com.simon.harmonichackernews.utils.Utils

/** Android cache/image/link adapter around the platform-neutral submissions screen. */
@Composable
internal fun SubmissionsScreen(controller: SubmissionsComposeController) {
    val context = LocalContext.current
    SharedSubmissionsScreen(
        controller = controller,
        storyItemModel = ::rememberAndroidStoryItemUiModel,
        onOpenLink = { Utils.openLinkMaybeHN(context, it) },
    )
}

@Composable
private fun rememberAndroidStoryItemUiModel(
    story: Story,
    settings: StoryDisplaySettings,
): StoryItemUiModel {
    val context = LocalContext.current
    val cachedPreviewUrl = remember(story.id, story.url) {
        story.previewImageUrl ?: runCatching {
            StoryPreviewImageLoader.getCachedPreviewImageUrl(context, story.id, story.url)
        }.getOrNull()
    }
    val cachedSummary = remember(story.id, story.url, settings.showSummary) {
        if (settings.showSummary) {
            StoryPreviewImageLoader.getCachedLinkSummary(context, story.url)?.description
        } else {
            null
        }
    }
    var previewUrl by remember(story.id, story.url) { mutableStateOf(cachedPreviewUrl) }
    var summary by remember(story.id, story.url) {
        mutableStateOf(story.summary ?: story.linkSummaryDescription ?: cachedSummary.orEmpty())
    }

    DisposableEffect(
        story.id,
        story.url,
        settings.previewImageMode,
        settings.showSummary,
    ) {
        val needsPreview = settings.previewImageMode != StoryPreviewPreferences.OFF
        val request = if (story.isLink && !story.url.isNullOrBlank() &&
            (needsPreview || settings.showSummary)
        ) {
            StoryPreviewImageLoader.loadPreviewContent(
                context,
                story.id,
                story.url,
                settings.showSummary,
            ) { imageUrl, result ->
                story.previewImageUrl = imageUrl
                story.previewImageUrlLoaded = true
                previewUrl = imageUrl
                result?.description?.let {
                    story.linkSummaryDescription = it
                    story.linkSummaryLoaded = true
                    summary = it
                }
            }
        } else {
            null
        }
        onDispose { request?.cancel() }
    }

    val fullDomain = remember(story.url) {
        runCatching { story.getDisplayDomain(true) }.getOrDefault("")
    }
    val shortDomain = remember(story.url) {
        runCatching { story.getDisplayDomain(false) }.getOrDefault(fullDomain)
    }
    val faviconUrl = remember(story.url, settings.faviconProvider) {
        runCatching { FaviconLoader.getFaviconUrl(story.url, settings.faviconProvider) }
            .getOrNull()
    }
    val tintBaseColor = remember(context) {
        PreviewImageTintUtils.getTintBaseColor(context)
    }
    val previewTintArgb = remember(
        story.id,
        previewUrl,
        tintBaseColor,
        story.previewImageTintColorLoaded,
    ) {
        previewUrl?.let { sourceUrl ->
            if (story.previewImageTintColorLoaded &&
                story.previewImageTintSourceUrl == sourceUrl &&
                story.previewImageTintBaseColor == tintBaseColor
            ) {
                story.previewImageTintColor
            } else {
                StoryPreviewImageLoader.loadCachedPreviewImageTintColor(
                    context,
                    story.id,
                    sourceUrl,
                    tintBaseColor,
                )
            }
        }
    }
    val faviconTintArgb = remember(
        story.id,
        faviconUrl,
        tintBaseColor,
        story.faviconTintColorLoaded,
    ) {
        faviconUrl?.let { sourceUrl ->
            if (story.faviconTintColorLoaded &&
                story.faviconTintSourceUrl == sourceUrl &&
                story.faviconTintBaseColor == tintBaseColor
            ) {
                story.faviconTintColor
            } else {
                StoryPreviewImageLoader.loadCachedPreviewImageTintColor(
                    context,
                    story.id,
                    sourceUrl,
                    tintBaseColor,
                )
            }
        }
    }

    return StoryItemUiModel(
        index = "",
        title = story.title.orEmpty(),
        summary = summary,
        points = story.score,
        domain = fullDomain.orEmpty(),
        domainWithoutTopLevel = shortDomain.orEmpty(),
        age = story.timeFormatted,
        commentCount = story.descendants,
        faviconFallback = Res.drawable.ic_public,
        previewImageFallback = null,
        faviconUrl = faviconUrl,
        previewImageUrl = previewUrl,
        faviconTintArgb = faviconTintArgb,
        previewImageTintArgb = previewTintArgb,
        tintFallbackArgb = tintBaseColor,
    )
}
