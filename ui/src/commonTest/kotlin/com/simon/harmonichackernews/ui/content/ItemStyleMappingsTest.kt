package com.simon.harmonichackernews.ui.content

import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.CommentDepthPreferences
import com.simon.harmonichackernews.settings.StoryPreviewMode
import kotlin.test.Test
import kotlin.test.assertEquals

class ItemStyleMappingsTest {
    @Test
    fun storyStyleCombinesDisplaySettingsWithScreenContext() {
        val settings = storySettings()
        val cases = listOf(
            StoryStyleCase(
                name = "stories row with unavailable summary",
                context = StoryItemStyleContext(
                    score = 80,
                    commentCount = 21,
                    clicked = true,
                    summaryAvailable = false,
                ),
                expected = storyStyle(
                    showSummary = false,
                    showIndex = true,
                    useHotnessIcon = true,
                    dimmed = true,
                ),
            ),
            StoryStyleCase(
                name = "submission row at the hotness threshold",
                context = StoryItemStyleContext(
                    score = 40,
                    commentCount = 60,
                    clicked = false,
                    showIndex = false,
                ),
                expected = storyStyle(
                    showSummary = true,
                    showIndex = false,
                    useHotnessIcon = false,
                    dimmed = false,
                ),
            ),
        )

        cases.forEach { case ->
            assertEquals(case.expected, settings.toStoryItemStyle(case.context), case.name)
        }
    }

    @Test
    fun commentStyleAppliesOnlyTheOverridesForItsScreen() {
        val settings = commentSettings()
        val cases = listOf(
            CommentStyleCase(
                name = "animated thread",
                context = CommentItemStyleContext.Thread(animateChanges = true),
                expected = threadCommentStyle(animateChanges = true),
            ),
            CommentStyleCase(
                name = "non-animated thread",
                context = CommentItemStyleContext.Thread(animateChanges = false),
                expected = threadCommentStyle(animateChanges = false),
            ),
            CommentStyleCase(
                name = "flat search result",
                context = CommentItemStyleContext.Search,
                expected = CommentItemStyle(
                    cardStyle = true,
                    showCardBorder = false,
                    textSize = 18.5f,
                    collectLinks = false,
                    emphasizeMeta = false,
                    depthIndicatorMode = CommentDepthPreferences.NONE,
                    showDivider = false,
                    preferredFont = "serif",
                    animateChanges = false,
                    transparentNonCardBackground = true,
                ),
            ),
        )

        cases.forEach { case ->
            assertEquals(case.expected, settings.toCommentItemStyle(case.context), case.name)
        }
    }

    private fun storySettings() = StoryDisplaySettings(
        showPoints = false,
        compactPoints = true,
        includeTopLevelDomain = false,
        showCommentsCount = true,
        compactView = true,
        thumbnails = false,
        previewImageMode = StoryPreviewMode.LARGE,
        borderlessLargePreviewImage = true,
        showSummary = true,
        storyTextSize = 17.5f,
        showIndex = true,
        compactHeader = false,
        leftAlign = true,
        cardStyle = false,
        tintCardUsingPreview = true,
        paletteTintMode = "vibrant:0.75",
        grayOutClicked = true,
        hotness = 100,
        faviconProvider = "example",
        font = "serif",
        commentTextSize = 18.5f,
    )

    private fun storyStyle(
        showSummary: Boolean,
        showIndex: Boolean,
        useHotnessIcon: Boolean,
        dimmed: Boolean,
    ) = StoryItemStyle(
        previewImageMode = StoryPreviewMode.LARGE,
        borderlessLargeImage = true,
        compact = true,
        showSummary = showSummary,
        showFavicon = false,
        showPoints = false,
        compactPoints = true,
        includeTopLevelDomain = false,
        showCommentCount = true,
        showIndex = showIndex,
        commentsOnLeft = true,
        tintCard = true,
        cardStyle = false,
        useHotnessIcon = useHotnessIcon,
        preferredFont = "serif",
        textSize = 17.5f,
        dimmed = dimmed,
        paletteTintConfigKey = "vibrant:0.75",
    )

    private fun commentSettings() = CommentDisplaySettings(
        collapseParent = false,
        showThumbnail = false,
        showHeaderPreviewImage = false,
        tintHeader = false,
        showUpButton = false,
        paletteTintMode = "default",
        preferredTextSize = 18.5f,
        commentDepthIndicatorMode = "threads",
        showNavigationBar = false,
        font = "serif",
        showInvert = false,
        showTopLevelDepthIndicator = false,
        theme = null,
        isTablet = false,
        faviconProvider = "example",
        swapLongPressTap = false,
        cardStyle = true,
        cardBorder = false,
        showDividers = true,
        highlightCommentMeta = false,
        collectReferenceLinks = true,
        hasAccountDetails = false,
        canProvideSummary = false,
        showAdditionalSummaryInfo = false,
        enableSummaryBoldFormatting = true,
    )

    private fun threadCommentStyle(animateChanges: Boolean) = CommentItemStyle(
        cardStyle = true,
        showCardBorder = false,
        textSize = 18.5f,
        collectLinks = true,
        emphasizeMeta = false,
        depthIndicatorMode = "threads",
        showDivider = true,
        preferredFont = "serif",
        animateChanges = animateChanges,
    )

    private data class StoryStyleCase(
        val name: String,
        val context: StoryItemStyleContext,
        val expected: StoryItemStyle,
    )

    private data class CommentStyleCase(
        val name: String,
        val context: CommentItemStyleContext,
        val expected: CommentItemStyle,
    )
}
