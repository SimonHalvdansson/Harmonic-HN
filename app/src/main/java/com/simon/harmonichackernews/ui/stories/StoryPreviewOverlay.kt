package com.simon.harmonichackernews.ui.stories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.utils.AndroidDisplay
import com.simon.harmonichackernews.utils.HtmlTextUtils

@Composable
internal fun StoryPreviewOverlay(controller: StoriesComposeController) {
    val context = LocalContext.current
    val dependencies = LocalHarmonicUiDependencies.current
    val fallbackSettings = remember(dependencies.userSettings.story) {
        StoryDisplaySettings.from(dependencies.userSettings.story)
    }
    val displaySettings = controller.displaySettings ?: fallbackSettings
    val contentVersion = controller.contentVersion
    val hasAccount = remember(contentVersion) { dependencies.platform.accounts.load() != null }
    val userSettings = dependencies.userSettings
    val bookmarksEnabled = userSettings.general.bookmarksEnabled
    val faviconProvider = userSettings.story.faviconProvider

    SharedStoryPreviewOverlay(
        controller = controller,
        tablet = AndroidDisplay.isTablet(context.resources),
    ) { story, page, cardColor, modifier ->
        val previewResource = controller.previewResources[story.id]
            ?.takeIf { it.pageUrl == story.url }
        val summaryState = StoryPreviewSummaryState(
            loading = previewResource?.loading == true,
            result = previewResource?.summary,
        )
        val faviconUrl = remember(story.id, story.url, faviconProvider) {
            if (!story.isLink || story.url.isNullOrBlank()) {
                null
            } else {
                runCatching {
                    FaviconUrlBuilder.faviconUrl(story.url.orEmpty(), faviconProvider)
                }.getOrNull()
            }
        }
        SharedStoryPreviewCard(
            controller = controller,
            story = story,
            page = page,
            cardColor = cardColor,
            settings = displaySettings,
            summaryState = summaryState,
            previewResource = previewResource,
            hasAccount = hasAccount,
            bookmarksEnabled = bookmarksEnabled,
            faviconUrl = faviconUrl,
            textStyle = storyPreviewTextStyle,
            htmlToPlainText = HtmlTextUtils::plainText,
            onPreviewImageLoaded = controller.listener::onStoryPreviewImageLoaded,
            onPreviewImageError = controller.listener::onStoryPreviewImageLoadFailed,
            modifier = modifier,
        )
    }
}

private val storyPreviewTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
