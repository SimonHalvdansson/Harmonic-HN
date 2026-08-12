package com.simon.harmonichackernews.ui.stories

import android.text.Html
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.Utils

@Composable
internal fun StoryPreviewOverlay(controller: StoriesComposeController) {
    val context = LocalContext.current
    val fallbackSettings = remember(context) {
        StoryDisplaySettings.from(AndroidUserSettings(context).story)
    }
    val displaySettings = controller.displaySettings ?: fallbackSettings
    val contentVersion = controller.contentVersion
    val hasAccount = remember(contentVersion) { AccountUtils.hasAccountDetails(context) }
    val userSettings = remember(contentVersion) { AndroidUserSettings.get(context) }
    val bookmarksEnabled = userSettings.general.bookmarksEnabled
    val faviconProvider = userSettings.story.faviconProvider

    SharedStoryPreviewOverlay(
        controller = controller,
        tablet = Utils.isTablet(context.resources),
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
            htmlToPlainText = { html ->
                Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
            },
            onPreviewImageLoaded = controller.listener::onStoryPreviewImageLoaded,
            onPreviewImageError = controller.listener::onStoryPreviewImageLoadFailed,
            modifier = modifier,
        )
    }
}

private val storyPreviewTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
