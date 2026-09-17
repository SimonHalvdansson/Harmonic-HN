package com.simon.harmonichackernews.ui.stories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.platform.accountOrNull
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.utils.AndroidDisplay
import com.simon.harmonichackernews.utils.HtmlTextUtils

@Composable
internal fun AndroidStoryPreviewOverlay(
    controller: StoriesComposeController,
    onScrimAlphaChanged: (Float) -> Unit = {},
) {
    val dependencies = LocalHarmonicUiDependencies.current
    val fallbackSettings = remember(dependencies.userSettings.story) {
        StoryDisplaySettings.from(dependencies.userSettings.story)
    }
    val displaySettings = controller.displaySettings ?: fallbackSettings
    val accountState by dependencies.platform.accounts.accountState.collectAsState()
    val hasAccount = accountState.accountOrNull != null
    val userSettings = dependencies.userSettings
    val bookmarksEnabled = userSettings.general.bookmarksEnabled
    val faviconProvider = userSettings.story.faviconProvider

    StoryPreviewOverlay(
        controller = controller,
        tablet = AndroidDisplay.isTablet(LocalResources.current),
        onScrimAlphaChanged = onScrimAlphaChanged,
    ) { story, page, cardColor, modifier ->
        val previewResource = controller.previewResource(story.id)
            ?.takeIf { it.pageUrl == story.url }
        val summaryState = StoryPreviewSummaryState(
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
        StoryPreviewCard(
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
