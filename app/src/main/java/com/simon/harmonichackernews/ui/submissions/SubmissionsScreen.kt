package com.simon.harmonichackernews.ui.submissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.network.StoryResourceTintKind
import com.simon.harmonichackernews.presentation.StoryListResourceRuntime
import com.simon.harmonichackernews.ui.content.StoryItemResourcePresentation
import com.simon.harmonichackernews.ui.content.StoryItemUiModel
import com.simon.harmonichackernews.ui.content.StoryItemUiModelFactory
import com.simon.harmonichackernews.ui.content.withPreviewResource
import com.simon.harmonichackernews.utils.AndroidLinkNavigation
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

/** Android cache and link adapters around the platform-neutral submissions screen. */
@Composable
internal fun SubmissionsScreen(controller: SubmissionsComposeController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appComposition = LocalHarmonicUiDependencies.current
    val previewResources = remember(scope, appComposition) {
        StoryListResourceRuntime(
            scope = scope,
            service = appComposition.previewResources,
            settings = controller.displaySettings,
            tintStore = appComposition.storyResourceTints,
        )
    }
    SideEffect { previewResources.updateSettings(controller.displaySettings) }
    val previewStates by previewResources.statesFlow.collectAsState()
    DisposableEffect(previewResources) {
        onDispose(previewResources::dispose)
    }
    SharedSubmissionsScreen(
        controller = controller,
        previewResources = previewResources,
        storyItemModel = { story, settings ->
            rememberAndroidStoryItemUiModel(
                story = story,
                settings = settings,
                previewState = previewStates[story.id],
                previewResources = previewResources,
            )
        },
        onOpenLink = { AndroidLinkNavigation.openMaybeHackerNews(context, it) },
    )
}

@Composable
private fun rememberAndroidStoryItemUiModel(
    story: Story,
    settings: StoryDisplaySettings,
    previewState: StoryPreviewResourceState?,
    previewResources: StoryListResourceRuntime,
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
        runCatching { FaviconUrlBuilder.faviconUrl(story.url.orEmpty(), settings.faviconProvider) }
            .getOrNull()
    }
    val tintBaseColor = HarmonicTheme.colors.surfaceContainerHigh.toArgb()

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
