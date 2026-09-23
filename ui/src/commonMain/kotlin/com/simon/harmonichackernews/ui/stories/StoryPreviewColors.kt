package com.simon.harmonichackernews.ui.stories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.content.paletteCompatible
import com.simon.harmonichackernews.ui.content.rememberCoilImagePaletteTint
import com.simon.harmonichackernews.ui.content.rememberPreviewImagePaletteTint
import com.simon.harmonichackernews.ui.content.storyItemUiModel
import com.simon.harmonichackernews.ui.theme.HarmonicTheme

/** Resolve against the live theme without replacing the deck or resetting its pager/animations. */
@Composable
internal fun rememberStoryPreviewCardColor(
    controller: StoriesComposeController,
    story: StoryListItemSnapshot,
): Color {
    val dependencies = LocalHarmonicUiDependencies.current
    val settings = controller.displaySettings
        ?: StoryDisplaySettings.from(dependencies.userSettings.story)
    val baseColor = HarmonicTheme.colors.storyCardBackground
    if (!settings.tintCardUsingPreview) {
        return if (settings.hasBackground) baseColor else HarmonicTheme.colors.background
    }
    val baseArgb = baseColor.toArgb()
    val revision = controller.storyRevision(story.id)
    val resource = controller.previewResource(story.id)?.takeIf { it.pageUrl == story.url }
    val model = remember(story, settings, baseArgb, revision, resource) {
        storyItemUiModel(story, 0, settings, resource, baseArgb, dependencies.storyResourceTints)
    }
    val previewUrl = model.previewImageUrl?.takeIf {
        settings.previewImageMode != StoryPreviewMode.OFF && resource?.imageLoadFailed != true
    }
    val previewTint = previewUrl?.let { url ->
        model.previewImageTintArgb ?: rememberPreviewResourceTint(
            controller, story, url, baseArgb, settings.paletteTintMode, favicon = false,
        )
    }
    val faviconTint = if (previewTint == null) {
        model.faviconTintArgb ?: model.faviconUrl?.let { url ->
            rememberPreviewResourceTint(
                controller, story, url, baseArgb, settings.paletteTintMode, favicon = true,
            )
        }
    } else null
    return Color(previewTint ?: faviconTint ?: baseArgb)
}

/** A paged preview must also refresh tints when its source list row is no longer composed. */
@Composable
private fun rememberPreviewResourceTint(
    controller: StoriesComposeController,
    story: StoryListItemSnapshot,
    url: String,
    baseArgb: Int,
    paletteMode: String,
    favicon: Boolean,
): Int? {
    val context = LocalPlatformContext.current
    val request = remember(context, url) {
        // Only palette pixels are needed here; do not decode a full-resolution article image.
        ImageRequest.Builder(context).data(url).size(256, 256).paletteCompatible().build()
    }
    val painter = rememberAsyncImagePainter(request)
    val state by painter.state.collectAsState()
    val success = state as? AsyncImagePainter.State.Success
    val tint = if (favicon) {
        rememberCoilImagePaletteTint(
            success?.result?.image, success?.painter, baseArgb, paletteMode,
            sharedCacheKey = url,
        )
    } else {
        rememberPreviewImagePaletteTint(
            success?.result?.image, success?.painter, baseArgb, paletteMode, enabled = true,
        )
    }
    LaunchedEffect(tint, url, baseArgb, paletteMode) {
        tint?.let {
            controller.listener.onStoryTintExtracted(story, url, baseArgb, paletteMode, it, favicon)
            controller.invalidateStory(story.id)
        }
    }
    return tint
}
