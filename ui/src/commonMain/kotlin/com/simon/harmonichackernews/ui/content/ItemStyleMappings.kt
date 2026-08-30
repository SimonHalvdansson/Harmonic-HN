package com.simon.harmonichackernews.ui.content

import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.CommentDepthPreferences

/** Screen-specific facts that are not part of the user's story display preferences. */
internal data class StoryItemStyleContext(
    val score: Int,
    val commentCount: Int,
    val clicked: Boolean,
    val summaryAvailable: Boolean = true,
    val showIndex: Boolean? = null,
)

internal fun StoryDisplaySettings.toStoryItemStyle(
    context: StoryItemStyleContext,
): StoryItemStyle = StoryItemStyle(
    previewImageMode = previewImageMode,
    borderlessLargeImage = borderlessLargePreviewImage,
    compact = compactView,
    showSummary = showSummary && context.summaryAvailable,
    showFavicon = thumbnails,
    showPoints = showPoints,
    compactPoints = compactPoints,
    includeTopLevelDomain = includeTopLevelDomain,
    showCommentCount = showCommentsCount,
    showIndex = context.showIndex ?: showIndex,
    commentsOnLeft = leftAlign,
    tintCard = tintCardUsingPreview,
    cardStyle = cardStyle,
    useHotnessIcon = hotness > 0 && context.score + context.commentCount > hotness,
    preferredFont = font,
    textSize = storyTextSize,
    dimmed = grayOutClicked && context.clicked,
    paletteTintConfigKey = paletteTintMode,
)

internal sealed interface CommentItemStyleContext {
    data class Thread(val animateChanges: Boolean) : CommentItemStyleContext
    data object Search : CommentItemStyleContext
}

internal fun CommentDisplaySettings.toCommentItemStyle(
    context: CommentItemStyleContext,
): CommentItemStyle = when (context) {
    is CommentItemStyleContext.Thread -> CommentItemStyle(
        cardStyle = cardStyle,
        showCardBorder = cardBorder,
        textSize = preferredTextSize,
        collectLinks = collectReferenceLinks,
        emphasizeMeta = highlightCommentMeta,
        depthIndicatorMode = commentDepthIndicatorMode,
        showDivider = showDividers,
        preferredFont = font,
        animateChanges = context.animateChanges,
    )
    CommentItemStyleContext.Search -> CommentItemStyle(
        cardStyle = cardStyle,
        showCardBorder = cardBorder,
        textSize = preferredTextSize,
        collectLinks = false,
        emphasizeMeta = highlightCommentMeta,
        depthIndicatorMode = CommentDepthPreferences.NONE,
        showDivider = false,
        preferredFont = font,
        animateChanges = false,
        transparentNonCardBackground = true,
    )
}
