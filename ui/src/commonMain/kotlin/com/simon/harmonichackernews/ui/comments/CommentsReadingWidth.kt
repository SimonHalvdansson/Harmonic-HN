package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Size against the actual comments pane, including when a direct story link has no list pane.
 * Apply inside full-width backgrounds and before existing content/safe-area padding so headers
 * and rows share a reading column without narrowing the screen's scrolling surface.
 */
internal fun Modifier.commentsReadingWidth(): Modifier =
    fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = 720.dp)
