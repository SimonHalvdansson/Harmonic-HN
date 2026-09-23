package com.simon.harmonichackernews.ui.content

import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.CommentDepthPreferences

/** Screen-specific facts that are not part of the user's story display preferences. */
internal data class StoryRowStyleContext(
    val score: Int,
    val commentCount: Int,
    val isRead: Boolean,
    val previewTextAvailable: Boolean = true,
    val showIndex: Boolean? = null,
)

internal fun StoryDisplaySettings.toStoryRowStyle(
    context: StoryRowStyleContext,
): StoryRowStyle = StoryRowStyle(
    previewImageMode = previewImageMode,
    borderlessLargeImage = borderlessLargePreviewImage,
    compact = compactView,
    showPreviewText = showPreviewText && context.previewTextAvailable,
    showFavicon = showFavicons,
    showPoints = showPoints,
    compactPoints = compactPoints,
    includeTopLevelDomain = includeTopLevelDomain,
    showCommentCount = showCommentsCount,
    showIndex = context.showIndex ?: showIndex,
    commentsOnLeft = commentsButtonOnLeft,
    tintCard = tintCardsFromImages,
    displayStyle = displayStyle,
    useHotnessIcon = hotnessThreshold > 0 && context.score + context.commentCount > hotnessThreshold,
    preferredFont = font,
    textSize = storyTextSize,
    dimmed = dimReadStories && context.isRead,
    paletteTintConfigKey = paletteTintMode,
)

internal sealed interface CommentRowStyleContext {
    data class Thread(val animateChanges: Boolean) : CommentRowStyleContext
    data object Search : CommentRowStyleContext
}

internal fun CommentDisplaySettings.toCommentRowStyle(
    context: CommentRowStyleContext,
): CommentRowStyle = when (context) {
    is CommentRowStyleContext.Thread -> CommentRowStyle(
        displayStyle = displayStyle,
        textSize = preferredTextSize,
        collectLinks = collectReferenceLinks,
        expandedReferenceLinks = expandedReferenceLinks,
        emphasizeMeta = highlightCommentMeta,
        depthIndicatorMode = commentDepthIndicatorMode,
        indicatorThickness = indicatorThickness,
        roundedDepthIndicators = roundedDepthIndicators,
        continuousDepthIndicators = continuousDepthIndicators,
        userAvatarsEnabled = userAvatarsEnabled,
        userAvatarOptions = userAvatarOptions,
        showDivider = showDividers,
        markNewComments = markNewComments,
        preferredFont = font,
        animateChanges = context.animateChanges,
    )
    CommentRowStyleContext.Search -> CommentRowStyle(
        displayStyle = displayStyle,
        textSize = preferredTextSize,
        collectLinks = false,
        emphasizeMeta = highlightCommentMeta,
        depthIndicatorMode = CommentDepthPreferences.NONE,
        userAvatarsEnabled = userAvatarsEnabled,
        userAvatarOptions = userAvatarOptions,
        showDivider = false,
        preferredFont = font,
        animateChanges = false,
        transparentNonCardBackground = true,
    )
}
