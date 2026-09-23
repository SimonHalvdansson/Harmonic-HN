@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.comments


import com.simon.harmonichackernews.resources.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.summary.GEMINI_NANO_POLICY_BLOCKED_MESSAGE
import com.simon.harmonichackernews.summary.StorySummaryDiagnostics
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import org.jetbrains.compose.resources.painterResource

@Composable
fun StoryAiSummary(
    story: StoryListItemSnapshot,
    settings: CommentDisplaySettings,
    onOpenLink: (String) -> Unit,
    diagnostics: StorySummaryDiagnostics? = null,
    streaming: Boolean = false,
    containerColor: Color = HarmonicTheme.colors.surfaceContainerHigh,
) {
    val summary = story.aiSummaryText.orEmpty()
    var showInfoDialog by remember(story.id) { mutableStateOf(false) }
    val policyBlocked = !story.summaryGeneratedSuccessfully &&
        summary == GEMINI_NANO_POLICY_BLOCKED_MESSAGE
    val typography = rememberContentTypography(
        preferredFont = settings.font,
        commentTextSize = settings.preferredTextSize,
    )
    AnimatedVisibility(
        visible = summary.isNotBlank(),
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .animateContentSize(
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    alignment = Alignment.TopStart,
                )
                .clip(RoundedCornerShape(14.dp))
                .background(containerColor)
                .border(1.dp, HarmonicTheme.colors.commentDivider, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(Res.drawable.ic_auto_awesome),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 7.dp)
                        .size(14.dp),
                )
                Text(
                    "Summary",
                    fontFamily = typography.family,
                    fontWeight = FontWeight.Bold,
                    color = HarmonicTheme.colors.contentPrimary,
                )
                Spacer(Modifier.weight(1f))
                if (settings.showAdditionalSummaryInfo) {
                    IconButton(
                        onClick = { showInfoDialog = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_info),
                            contentDescription = "AI summary info",
                            modifier = Modifier.size(18.dp),
                            tint = HarmonicTheme.colors.textSecondary,
                        )
                    }
                }
            }
            if (policyBlocked) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_block),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = summary,
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = typography.family,
                        fontSize = typography.commentTextSize.sp,
                        lineHeight = (typography.commentTextSize + 2f).sp,
                    )
                }
            } else if (summary.isNotBlank()) {
                SelectionContainer {
                    SummaryMarkdownText(
                        markdown = summary,
                        baseUrl = story.url ?: "https://news.ycombinator.com/item?id=${story.id}",
                        onOpenLink = onOpenLink,
                        modifier = Modifier.padding(top = 4.dp),
                        color = HarmonicTheme.colors.contentPrimary,
                        linkColor = HarmonicTheme.colors.link,
                        fontFamily = typography.family,
                        fontSize = typography.commentTextSize.sp,
                        lineHeight = (typography.commentTextSize + 2f).sp,
                        enableBoldFormatting = settings.enableSummaryBoldFormatting,
                        animateStreamingText = streaming,
                        animationContentKey = story.id,
                    )
                }
            }
        }
    }
    if (showInfoDialog) {
        StorySummaryInfoDialog(
            summary = summary,
            diagnostics = diagnostics,
            streaming = streaming,
            onDismiss = { showInfoDialog = false },
        )
    }
}
