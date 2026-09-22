package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.ui.content.CommentItemStyle
import com.simon.harmonichackernews.ui.content.StoryItemStyle

internal fun StoriesSettingsUiState.toPreviewStoryItemStyle(): StoryItemStyle = StoryItemStyle(
    previewImageMode = previewImageMode,
    borderlessLargeImage = borderlessLargeImage,
    compact = compact,
    showSummary = showSummary,
    showFavicon = showThumbnails,
    showPoints = showPoints,
    compactPoints = compactPoints,
    includeTopLevelDomain = includeTopLevelDomain,
    showCommentCount = showComments,
    showIndex = showIndex,
    commentsOnLeft = leftAlignComments,
    tintCard = tint,
    displayStyle = DisplayStyle.fromStored(displayStyle),
    useHotnessIcon = hotnessEnabled,
    preferredFont = preferredFont,
    textSize = textSize,
    paletteTintConfigKey = paletteTintConfigKey,
)

internal fun CommentsSettingsUiState.toPreviewCommentItemStyle(): CommentItemStyle =
    CommentItemStyle(
        displayStyle = displayStyle,
        textSize = textSize,
        collectLinks = collectLinks,
        expandedReferenceLinks = expandedReferenceLinks,
        emphasizeMeta = emphasizeMetadata,
        depthIndicatorMode = depthMode,
        indicatorThickness = indicatorThickness,
        roundedDepthIndicators = roundedDepthIndicators,
        continuousDepthIndicators = continuousDepthIndicators,
        userAvatarsEnabled = userAvatarsEnabled,
        userAvatarOptions = userAvatarOptions,
        showDivider = showDividers,
        markNewComments = markNewComments,
        preferredFont = preferredFont,
        animateChanges = animateChanges,
    )
