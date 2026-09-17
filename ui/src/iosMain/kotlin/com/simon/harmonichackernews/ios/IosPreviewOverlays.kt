package com.simon.harmonichackernews.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.platform.accountOrNull
import com.simon.harmonichackernews.ui.stories.StoryPreviewCard
import com.simon.harmonichackernews.ui.stories.StoryPreviewOverlay
import com.simon.harmonichackernews.ui.stories.StoriesComposeController
import com.simon.harmonichackernews.ui.stories.StoryPreviewSummaryState
import com.simon.harmonichackernews.utils.HtmlTextUtils

@Composable
internal fun IosStoryPreviewOverlay(
    app: HarmonicAppComposition,
    controller: StoriesComposeController,
) {
    val fallbackSettings = remember(app.userSettings.story) {
        StoryDisplaySettings.from(app.userSettings.story)
    }
    val displaySettings = controller.displaySettings ?: fallbackSettings
    val accountState by app.platform.accounts.accountState.collectAsState()
    val hasAccount = accountState.accountOrNull != null
    val bookmarksEnabled = app.userSettings.general.bookmarksEnabled
    val faviconProvider = app.userSettings.story.faviconProvider

    StoryPreviewOverlay(
        controller = controller,
        tablet = true,
    ) { story, page, cardColor, modifier ->
        val previewResource = controller.previewResource(story.id)
            ?.takeIf { it.pageUrl == story.url }
        val faviconUrl = remember(story.id, story.url, faviconProvider) {
            story.url
                ?.takeIf { story.isLink && it.isNotBlank() }
                ?.let { url ->
                    runCatching { FaviconUrlBuilder.faviconUrl(url, faviconProvider) }.getOrNull()
                }
        }
        StoryPreviewCard(
            controller = controller,
            story = story,
            page = page,
            cardColor = cardColor,
            settings = displaySettings,
            summaryState = StoryPreviewSummaryState(
                result = previewResource?.summary,
            ),
            previewResource = previewResource,
            hasAccount = hasAccount,
            bookmarksEnabled = bookmarksEnabled,
            faviconUrl = faviconUrl,
            textStyle = TextStyle.Default,
            htmlToPlainText = HtmlTextUtils::plainText,
            onPreviewImageLoaded = controller.listener::onStoryPreviewImageLoaded,
            onPreviewImageError = controller.listener::onStoryPreviewImageLoadFailed,
            modifier = modifier,
        )
    }
}
