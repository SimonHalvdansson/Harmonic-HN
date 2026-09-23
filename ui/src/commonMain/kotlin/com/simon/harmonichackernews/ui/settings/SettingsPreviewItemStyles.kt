package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.ui.content.CommentRowStyle
import com.simon.harmonichackernews.ui.content.StoryRowStyle

internal fun StoriesSettingsUiState.toPreviewStoryRowStyle(): StoryRowStyle = StoryRowStyle(
    previewImageMode = previewImageMode,
    borderlessLargeImage = borderlessLargeImage,
    compact = compact,
    showPreviewText = showPreviewText,
    showFavicon = showFavicons,
    showPoints = showPoints,
    compactPoints = compactPoints,
    includeTopLevelDomain = includeTopLevelDomain,
    showCommentCount = showComments,
    showIndex = showIndex,
    commentsOnLeft = commentsButtonOnLeft,
    tintCard = tint,
    displayStyle = DisplayStyle.fromStored(displayStyle),
    useHotnessIcon = hotnessEnabled,
    preferredFont = preferredFont,
    textSize = textSize,
    paletteTintConfigKey = paletteTintConfigKey,
)

internal fun CommentsSettingsUiState.toPreviewCommentRowStyle(): CommentRowStyle =
    CommentRowStyle(
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
