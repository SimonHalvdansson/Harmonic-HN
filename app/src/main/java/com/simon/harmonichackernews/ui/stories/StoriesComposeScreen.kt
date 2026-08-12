@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.simon.harmonichackernews.ui.stories

import org.jetbrains.compose.resources.DrawableResource


import com.simon.harmonichackernews.resources.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.AndroidAppComposition
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.network.StoryResourceTintKind
import com.simon.harmonichackernews.data.StoryResourceTintStore
import com.simon.harmonichackernews.presentation.StoriesInteractionStore
import com.simon.harmonichackernews.presentation.StoryFrontDatePickerRequest
import com.simon.harmonichackernews.presentation.StoryPredictiveBackSettleRequest
import com.simon.harmonichackernews.presentation.StoryPreviewActionKind
import com.simon.harmonichackernews.presentation.StoryPreviewOverlayState
import com.simon.harmonichackernews.presentation.StoryScrollRequest
import com.simon.harmonichackernews.presentation.SavedItemStateReader
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.StoryPreviewTintState
import com.simon.harmonichackernews.settings.StoryCachePreferences
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.HarmonicDropdownMenu
import com.simon.harmonichackernews.ui.content.HarmonicMenuText
import com.simon.harmonichackernews.ui.content.StoryItem
import com.simon.harmonichackernews.ui.content.StoryItemResourcePresentation
import com.simon.harmonichackernews.ui.content.StoryItemStyle
import com.simon.harmonichackernews.ui.content.StoryItemUiModel
import com.simon.harmonichackernews.ui.content.StoryItemUiModelFactory
import com.simon.harmonichackernews.ui.content.withPreviewResource
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.common.rememberHarmonicFilterColors
import com.simon.harmonichackernews.ui.common.SharedLazyContentList
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.settings.StoryPreviewPreferences
import com.simon.harmonichackernews.settings.TextPreferences
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

/**
 * Compose presentation bridge for the stories screen. The coordinator remains the data/network
 * controller during this migration; adapter notifications are converted to immutable snapshots.
 */
@Composable
internal fun StoriesScreen(controller: StoriesComposeController) {
    val context = LocalContext.current
    val tintBaseColor = HarmonicTheme.colors.surfaceContainerHigh.toArgb()
    val tintStore = remember(context) { AndroidAppComposition.get(context).storyResourceTints }
    SharedStoriesScreen(
        controller = controller,
        storyItemModel = { story, position, settings, previewResource ->
            story.toUiModel(position, settings, previewResource, tintBaseColor, tintStore)
        },
        commentText = { html ->
            runCatching { AnnotatedString.fromHtml(html) }.getOrElse { AnnotatedString(html) }
        },
        filterColors = rememberHarmonicFilterColors(),
        extraCompactSelectedText =
            booleanResource(R.bool.extra_compact_stories_dropdown_selected_text),
        compactSelectedText = booleanResource(R.bool.compact_stories_dropdown_selected_text),
    )
}

private fun Story.toUiModel(
    position: Int,
    settings: StoryDisplaySettings,
    previewResource: StoryPreviewResourceState?,
    tintBaseColor: Int,
    tintStore: StoryResourceTintStore,
): StoryItemUiModel {
    val favicon = runCatching {
        FaviconUrlBuilder.faviconUrl(url.orEmpty(), settings.faviconProvider)
    }.getOrNull()
    val paletteTintMode = PaletteTintPreferences.normalizeConfigKey(settings.paletteTintMode)
    val previewUrl = previewResource?.imageUrl ?: previewImageUrl
    val currentPreviewTint = StoryPreviewTintState.isPreviewCurrent(
        this,
        tintBaseColor,
        paletteTintMode,
    )
    val currentFaviconTint = faviconTintColorLoaded &&
        faviconTintBaseColor == tintBaseColor &&
        StoryPreviewTintState.isModeCurrent(faviconTintMode, paletteTintMode) &&
        faviconTintSourceUrl == favicon
    val persistedPreviewTint = previewUrl?.let { sourceUrl ->
        tintStore.read(
            id,
            StoryResourceTintKind.PREVIEW_IMAGE,
            sourceUrl,
            tintBaseColor,
            StoryPreviewTintState.storedMode(paletteTintMode),
        )?.tintColorArgb
    }
    val persistedFaviconTint = favicon?.let { sourceUrl ->
        tintStore.read(
            id,
            StoryResourceTintKind.FAVICON,
            sourceUrl,
            tintBaseColor,
            StoryPreviewTintState.storedMode(paletteTintMode),
        )?.tintColorArgb
    }
    return StoryItemUiModelFactory.create(
        story = this,
        position = position,
        resources = StoryItemResourcePresentation(
            faviconUrl = favicon,
            summary = linkSummaryDescription,
            previewImageUrl = previewUrl,
            previewImageLoadFailed = previewImageLoadFailed,
            faviconTintArgb = persistedFaviconTint
                ?: faviconTintColor.takeIf { currentFaviconTint },
            previewImageTintArgb = persistedPreviewTint
                ?: previewImageTintColor.takeIf { currentPreviewTint },
            tintFallbackArgb = tintBaseColor,
        ).withPreviewResource(previewResource, paletteTintMode),
    )
}


@Preview(name = "Phone", device = Devices.PIXEL_7, showBackground = true)
@Preview(name = "Fold inner", widthDp = 673, heightDp = 841, showBackground = true)
@Preview(name = "Tablet pane", widthDp = 600, heightDp = 960, showBackground = true)
@Composable
private fun StoryItemFormFactorPreview() {
    HarmonicTheme {
        StoryItem(
            model = SettingsStoryPreviewModel,
            style = StoryItemStyle(
                previewImageMode = StoryPreviewPreferences.SMALL,
                borderlessLargeImage = false,
                compact = false,
                showSummary = true,
                showFavicon = true,
                showPoints = true,
                compactPoints = false,
                includeTopLevelDomain = true,
                showCommentCount = true,
                showIndex = true,
                commentsOnLeft = false,
                tintCard = true,
                cardStyle = false,
                useHotnessIcon = false,
                preferredFont = "googlesansflexrounded",
                textSize = TextPreferences.DEFAULT_STORY_TEXT_SIZE,
            ),
        )
    }
}
