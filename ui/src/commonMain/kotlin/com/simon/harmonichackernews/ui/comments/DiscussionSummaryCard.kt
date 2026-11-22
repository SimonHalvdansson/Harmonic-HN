package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.summary.AskSource
import com.simon.harmonichackernews.ui.content.rememberContentTypography

@Composable
internal fun DiscussionSummaryCard(
    controller: CommentsScreenController,
    settings: CommentDisplaySettings,
    onOpenLink: (String) -> Unit,
) {
    val summary = controller.discussionSummary
    if (summary.text.isBlank()) return
    val typography = rememberContentTypography(settings.font, commentTextSize = settings.preferredTextSize)
    val story = controller.story
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    SummaryCard(
        title = "Discussion summary",
        subjectKey = story.id,
        subject = if (!summary.running) AskSource.Discussion(story) else null,
        fontFamily = typography.family,
        onAsk = if (controller.canSummarizeDiscussion) controller::openPostAsk else null,
        askVisible = controller.postAsk?.let { it.coveringSource && it.subject is AskSource.Discussion } == true,
        containerColorProvider = { color },
    ) {
        SelectionContainer(state = controller.discussionSelectionState) {
            SummaryMarkdownText(
                markdown = summary.text,
                baseUrl = "https://news.ycombinator.com/item?id=${story.id}",
                onOpenLink = onOpenLink,
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurface,
                linkColor = MaterialTheme.colorScheme.primary,
                fontFamily = typography.family,
                fontSize = typography.commentTextSize.sp,
                lineHeight = (typography.commentTextSize + 2f).sp,
                enableBoldFormatting = settings.enableSummaryBoldFormatting,
                animateStreamingText = summary.running,
                animationContentKey = story.id,
            )
        }
        if (summary.truncated) TruncatedResponseNotice(Modifier.padding(top = 8.dp))
    }
}
