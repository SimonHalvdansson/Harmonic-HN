@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.comments


import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.selected
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import com.simon.harmonichackernews.utils.AgePolicy
import com.simon.harmonichackernews.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.ui.common.relativeTimeOnResume
import com.simon.harmonichackernews.ui.content.ContentTypography
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun CommentsHeaderMetadata(
    story: StoryListItemSnapshot,
    settings: CommentDisplaySettings,
    storyPosterTag: String = "",
    textStyle: TextStyle,
    onAuthorClick: () -> Unit = {},
    onVoteClick: () -> Unit = {},
    onReplyClick: () -> Unit = {},
    upvoted: Boolean = false,
    voteLoading: Boolean = false,
    commented: Boolean = false,
    headerColor: Color = MaterialTheme.colorScheme.surface,
) {
    val commentIcon = if (commented) Res.drawable.ic_comment_filled else Res.drawable.ic_comment
    if (!story.loaded || settings.headerMetaInfo == com.simon.harmonichackernews.settings.CommentsHeaderMetaInfo.ON_IMAGE) return
    val typography = rememberContentTypography(settings.font)
    val combineActions = settings.combineHeaderActions && settings.hasAccountDetails
    val posterLabel = buildString {
        append(story.by.orEmpty())
        if (storyPosterTag.isNotBlank()) append(" (").append(storyPosterTag).append(')')
    }
    if (combineActions) {
        androidx.compose.foundation.layout.Column {
            if (!story.isLink || story.isComment || story.url.isNullOrBlank()) {
                HeaderMetaItem(Res.drawable.ic_schedule, relativeTimeOnResume(story.createdAtEpochSeconds),
                    typography, textStyle, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HeaderDetailButton(
                    icon = Res.drawable.ic_account_circle,
                    label = posterLabel.ifBlank { "User" },
                    description = "Open profile: ${story.by.orEmpty()}",
                    typography = typography, textStyle = textStyle,
                    headerColor = headerColor,
                    modifier = Modifier.weight(1.4f),
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                    onClick = onAuthorClick,
                )
                HeaderDetailButton(
                    icon = if (upvoted) Res.drawable.ic_thumb_up_filled else Res.drawable.ic_thumb_up,
                    label = if (story.isComment) "" else story.score.toString(),
                    description = if (upvoted) "Remove vote" else "Upvote",
                    typography = typography, textStyle = textStyle,
                    headerColor = headerColor,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                    loading = voteLoading, selected = upvoted, onClick = onVoteClick,
                )
                HeaderDetailButton(
                    icon = commentIcon, label = story.descendants.toString(),
                    description = (if (story.isComment) "Reply to comment" else "Reply to post") +
                        if (commented) ", you commented" else "",
                    typography = typography, textStyle = textStyle,
                    headerColor = headerColor,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp),
                    enabled = !AgePolicy.isOlderThanTwoWeeks(story.createdAtEpochSeconds),
                    highlighted = commented,
                    onClick = onReplyClick,
                )
            }
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowRow(
            modifier = Modifier
                .weight(1f)
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!story.isComment) {
                HeaderMetaItem(Res.drawable.ic_thumb_up, story.score.toString(), typography, textStyle)
            }
            HeaderMetaItem(commentIcon, story.descendants.toString(), typography, textStyle, highlighted = commented)
            HeaderMetaItem(Res.drawable.ic_schedule, relativeTimeOnResume(story.createdAtEpochSeconds), typography, textStyle)
            HeaderMetaItem(Res.drawable.ic_account_circle, posterLabel, typography, textStyle)

        }
        if (story.isLink) {
            Icon(
                painterResource(Res.drawable.ic_link),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeaderDetailButton(
    icon: DrawableResource,
    label: String,
    description: String,
    typography: ContentTypography,
    textStyle: TextStyle,
    modifier: Modifier,
    shape: Shape,
    headerColor: Color,
    enabled: Boolean = true,
    loading: Boolean = false,
    selected: Boolean = false,
    /** Tints the icon with the accent color, e.g. for stories the user has commented on. */
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        lerp(headerColor, colors.onSurface, if (selected) 0.18f else 0.07f),
        tween(180), label = "header detail button color",
    )
    val foreground by animateColorAsState(
        colors.onSurface,
        tween(180), label = "header detail button content",
    )
    Box(modifier) {
        CommentsTooltip(description) {
            FilledTonalButton(
                onClick = onClick, enabled = enabled && !loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { contentDescription = description; this.selected = selected },
                shape = shape, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = background, contentColor = foreground,
                    disabledContainerColor = background.copy(alpha = 0.5f),
                    disabledContentColor = foreground.copy(alpha = 0.38f),
                ),
            ) {
                AnimatedContent(targetState = icon to loading,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "header detail button icon") { (visibleIcon, busy) ->
                    if (busy) HarmonicLoadingIndicator(Modifier.size(18.dp))
                    else Icon(
                        painterResource(visibleIcon), null, Modifier.size(18.dp),
                        tint = if (highlighted) colors.primary else LocalContentColor.current,
                    )
                }
                if (label.isNotEmpty()) {
                    Text(label, Modifier.padding(start = 6.dp).weight(1f, fill = false),
                        fontFamily = typography.family, fontSize = typography.commentsHeaderMetaSize.sp,
                        fontWeight = FontWeight.Medium, style = textStyle,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun HeaderMetaItem(
    icon: DrawableResource,
    label: String,
    typography: ContentTypography,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            label,
            modifier = Modifier.padding(start = 3.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = typography.family,
            fontSize = typography.commentsHeaderMetaSize.sp,
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
