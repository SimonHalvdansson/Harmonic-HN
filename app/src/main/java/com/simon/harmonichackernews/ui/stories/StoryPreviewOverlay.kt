package com.simon.harmonichackernews.ui.stories

import android.text.Html
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.simon.harmonichackernews.adapters.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.FaviconLoader
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.StoryPreviewImageLoader
import com.simon.harmonichackernews.network.networkHeader
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils

@Composable
internal fun StoryPreviewOverlay(controller: StoriesComposeController) {
    val context = LocalContext.current
    val fallbackSettings = remember(context) {
        StoryDisplaySettings.from(AndroidUserSettings(context).story)
    }
    val settings = controller.displaySettings ?: fallbackSettings
    val contentVersion = controller.contentVersion
    val hasAccount = remember(contentVersion) { AccountUtils.hasAccountDetails(context) }
    val bookmarksEnabled = remember(contentVersion) { SettingsUtils.shouldUseBookmarks(context) }
    val faviconProvider = SettingsUtils.getPreferredFaviconProvider(context)

    SharedStoryPreviewOverlay(
        controller = controller,
        tablet = Utils.isTablet(context.resources),
    ) { story, page, cardColor, modifier ->
        val summaryState = rememberStorySummary(story, controller)
        val faviconUrl = remember(story.id, story.url, faviconProvider) {
            if (!story.isLink || story.url.isNullOrBlank()) {
                null
            } else {
                runCatching { FaviconLoader.getFaviconUrl(story.url, faviconProvider) }.getOrNull()
            }
        }
        SharedStoryPreviewCard(
            controller = controller,
            story = story,
            page = page,
            cardColor = cardColor,
            settings = settings,
            summaryState = summaryState,
            hasAccount = hasAccount,
            bookmarksEnabled = bookmarksEnabled,
            faviconUrl = faviconUrl,
            textStyle = storyPreviewTextStyle,
            htmlToPlainText = { html ->
                Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
            },
            previewImage = { url, onError, imageModifier ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .networkHeader("User-Agent", NetworkComponent.USER_AGENT)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = imageModifier,
                    contentScale = ContentScale.Crop,
                    onError = { onError() },
                )
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun rememberStorySummary(
    story: Story,
    controller: StoriesComposeController,
): StoryPreviewSummaryState {
    val context = LocalContext.current
    var state by remember(story.id, story.url) {
        mutableStateOf(
            if (!story.isLink || story.url.isNullOrBlank()) {
                StoryPreviewSummaryState(false)
            } else {
                StoryPreviewImageLoader.getCachedLinkSummary(context, story.url)?.let {
                    StoryPreviewSummaryState(false, it)
                } ?: StoryPreviewSummaryState(true)
            },
        )
    }
    LaunchedEffect(story.id, story.url, state.result?.imageUrl) {
        val imageUrl = state.result?.imageUrl?.takeIf(String::isNotBlank) ?: return@LaunchedEffect
        if (story.previewImageUrl != imageUrl || !story.previewImageUrlLoaded) {
            story.previewImageUrl = imageUrl
            story.previewImageUrlLoaded = true
            story.previewImageLoadFailed = false
            controller.invalidateStory(story.id)
        }
    }
    LaunchedEffect(story.id, story.url) {
        if (!story.isLink || story.url.isNullOrBlank() || state.result != null) {
            return@LaunchedEffect
        }
        val requestedUrl = story.url
        try {
            val result = NetworkComponent.linkSummaryRepository.load(
                requestedUrl.orEmpty(),
                story.title.orEmpty(),
            )
            StoryPreviewImageLoader.saveCachedLinkSummary(context, requestedUrl, result)
            if (story.url == requestedUrl) state = StoryPreviewSummaryState(false, result)
        } catch (_: Throwable) {
            if (story.url == requestedUrl) state = StoryPreviewSummaryState(false)
        }
    }
    return state
}

private val storyPreviewTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
