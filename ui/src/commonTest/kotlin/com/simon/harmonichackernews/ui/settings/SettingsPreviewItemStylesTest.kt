package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import com.simon.harmonichackernews.settings.CommentSortingPreference
import com.simon.harmonichackernews.settings.CommentVolumeNavigationMode
import com.simon.harmonichackernews.settings.CommentsProvider
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.content.CommentItemStyle
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.StoryItemStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsPreviewItemStylesTest {
    @Test
    fun storyPreviewStyleReflectsEachSettingsState() {
        val base = storiesState()
        val cases = listOf(
            base to StoryItemStyle(
                previewImageMode = StoryPreviewMode.SMALL,
                borderlessLargeImage = false,
                compact = true,
                showSummary = false,
                showFavicon = true,
                showPoints = false,
                compactPoints = true,
                includeTopLevelDomain = false,
                showCommentCount = true,
                showIndex = false,
                commentsOnLeft = true,
                tintCard = true,
                cardStyle = true,
                useHotnessIcon = true,
                preferredFont = "serif",
                textSize = 19f,
                paletteTintConfigKey = "muted:0.5",
            ),
            base.copy(
                displayStyle = "standard",
                showSummary = true,
                showIndex = true,
                hotnessEnabled = false,
            ) to StoryItemStyle(
                previewImageMode = StoryPreviewMode.SMALL,
                borderlessLargeImage = false,
                compact = true,
                showSummary = true,
                showFavicon = true,
                showPoints = false,
                compactPoints = true,
                includeTopLevelDomain = false,
                showCommentCount = true,
                showIndex = true,
                commentsOnLeft = true,
                tintCard = true,
                cardStyle = false,
                useHotnessIcon = false,
                preferredFont = "serif",
                textSize = 19f,
                paletteTintConfigKey = "muted:0.5",
            ),
        )

        cases.forEachIndexed { index, (state, expected) ->
            assertEquals(expected, state.toPreviewStoryItemStyle(), "story preview case $index")
        }
    }

    @Test
    fun commentPreviewStyleReflectsEachSettingsState() {
        val base = commentsState()
        val cases = listOf(
            base to CommentItemStyle(
                cardStyle = true,
                showCardBorder = true,
                textSize = 18f,
                collectLinks = true,
                emphasizeMeta = false,
                depthIndicatorMode = "colors",
                showDivider = true,
                preferredFont = "serif",
            ),
            base.copy(
                displayStyle = DisplayStyle.STANDARD,
                showBorder = false,
                collectLinks = false,
                emphasizeMetadata = true,
            ) to CommentItemStyle(
                cardStyle = false,
                showCardBorder = false,
                textSize = 18f,
                collectLinks = false,
                emphasizeMeta = true,
                depthIndicatorMode = "colors",
                showDivider = true,
                preferredFont = "serif",
            ),
        )

        cases.forEachIndexed { index, (state, expected) ->
            assertEquals(expected, state.toPreviewCommentItemStyle(), "comment preview case $index")
        }
    }

    private fun storiesState() = StoriesSettingsUiState(
        previewModel = SettingsStoryPreviewModel,
        previewImageMode = StoryPreviewMode.SMALL,
        borderlessLargeImage = false,
        compact = true,
        showSummary = false,
        showThumbnails = true,
        showPoints = false,
        compactPoints = true,
        includeTopLevelDomain = false,
        showComments = true,
        showIndex = false,
        leftAlignComments = true,
        tint = true,
        displayStyle = "card",
        standardStyleValue = "standard",
        cardStyleValue = "card",
        textSize = 19f,
        textSizeOffset = 3,
        minTextSizeOffset = -4,
        maxTextSizeOffset = 8,
        hotnessEnabled = true,
        hotnessLabel = "100",
        preferredFont = "serif",
        paletteTintConfigKey = "muted:0.5",
        startingPage = "Top",
        additionalFrontpagesSummary = "None",
        alwaysOpenComments = false,
        pagination = false,
        hideClicked = false,
        grayOutClicked = false,
        faviconProvider = "example",
        faviconIcon = TestPainter,
    )

    private fun commentsState() = CommentsSettingsUiState(
        displayStyle = DisplayStyle.CARD,
        showBorder = true,
        textSize = 18f,
        textSizeOffset = 2,
        minTextSizeOffset = -4,
        maxTextSizeOffset = 8,
        collectLinks = true,
        emphasizeMetadata = false,
        depthMode = "colors",
        depthModeLabel = "Colors",
        showDividers = true,
        preferredFont = "serif",
        topLevelIndicators = true,
        showScrollbar = true,
        animateChanges = true,
        storyTintEnabled = true,
        showUpButton = true,
        headerTint = true,
        storyPreviewEnabled = true,
        headerPreviewImage = true,
        collapseParent = false,
        collapseTopLevel = false,
        hideDelayedComments = false,
        preloadCommentsSummary = "Never",
        swapTap = false,
        sorting = CommentSortingPreference.DEFAULT,
        provider = CommentsProvider.ALGOLIA,
        showNavigationButtons = true,
        volumeNavigation = CommentVolumeNavigationMode.DISABLED,
        smoothScroll = true,
    )

    private object TestPainter : Painter() {
        override val intrinsicSize: Size = Size.Unspecified

        override fun DrawScope.onDraw() = Unit
    }
}
