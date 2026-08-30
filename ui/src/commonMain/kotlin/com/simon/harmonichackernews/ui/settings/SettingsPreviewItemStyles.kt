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
    cardStyle = displayStyle == cardStyleValue,
    useHotnessIcon = hotnessEnabled,
    preferredFont = preferredFont,
    textSize = textSize,
    paletteTintConfigKey = paletteTintConfigKey,
)

internal fun CommentsSettingsUiState.toPreviewCommentItemStyle(): CommentItemStyle =
    CommentItemStyle(
        cardStyle = displayStyle == DisplayStyle.CARD,
        showCardBorder = showBorder,
        textSize = textSize,
        collectLinks = collectLinks,
        emphasizeMeta = emphasizeMetadata,
        depthIndicatorMode = depthMode,
        showDivider = showDividers,
        preferredFont = preferredFont,
    )
