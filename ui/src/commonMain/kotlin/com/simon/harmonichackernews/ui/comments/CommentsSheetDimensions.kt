package com.simon.harmonichackernews.ui.comments

import androidx.compose.ui.unit.dp

internal val CommentsSheetHandleTopPadding = 8.dp
internal val CommentsSheetHandleBottomPadding = 4.dp
internal val CommentsSheetHandleHeight = 5.dp
internal val CommentsSheetButtonSize = 56.dp
internal val CommentsSheetHandleAreaHeight = CommentsSheetHandleTopPadding +
    CommentsSheetHandleHeight + CommentsSheetHandleBottomPadding

// Hosts add the current navigation inset separately, including taskbar size changes.
// Trim 1dp from below the controls without changing the handle or icon positions within the sheet.
val CommentsSheetCollapsedHeight = CommentsSheetHandleAreaHeight + CommentsSheetButtonSize - 1.dp
