package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_auto_awesome
import com.simon.harmonichackernews.summary.AskSource
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import com.simon.harmonichackernews.ui.common.TextButton
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun DiscussionSummaryCard(
    controller: CommentsScreenController,
    settings: CommentDisplaySettings,
    onOpenLink: (String) -> Unit,
) {
    val summary = controller.discussionSummary
    if (!controller.canSummarizeDiscussion) return
    val typography = rememberContentTypography(settings.font, commentTextSize = settings.preferredTextSize)
    if (!summary.running && summary.text.isEmpty() && summary.error == null) {
        TextButton(
            onClick = controller.listener::onSummarizeDiscussion,
            enabled = !controller.discussionRequestBlocked,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(painterResource(Res.drawable.ic_auto_awesome), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Summarize discussion", fontFamily = typography.family)
        }
        return
    }
    val story = controller.story
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    SummaryCard(
        title = "Discussion summary",
        subjectKey = story.id,
        subject = if (!summary.running && summary.text.isNotBlank()) AskSource.Discussion(story) else null,
        fontFamily = typography.family,
        onAsk = if (summary.error == null) controller::openPostAsk else null,
        askVisible = controller.postAsk?.let { it.coveringSource && it.subject is AskSource.Discussion } == true,
        containerColorProvider = { color },
        headerAction = {
            if (summary.running && summary.text.isEmpty()) HarmonicLoadingIndicator(Modifier.size(20.dp))
        },
    ) {
        val error = summary.error
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, fontFamily = typography.family)
            TextButton(onClick = controller.listener::onSummarizeDiscussion, enabled = !controller.discussionRequestBlocked) {
                Text("Try again")
            }
        } else if (summary.text.isNotBlank()) {
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
}
