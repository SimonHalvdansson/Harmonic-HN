package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.CommentsHeaderMetaInfo
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import com.simon.harmonichackernews.ui.content.compactStoryMetric
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.content.storyImageMetricBackground
import dev.chrisbanes.haze.HazeState
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Informational image badges; actions stay in the header toolbar. */
@Composable
internal fun HeaderImageMetrics(
    controller: CommentsScreenController,
    settings: CommentDisplaySettings,
    hazeState: HazeState,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        settings.headerMetaInfo == CommentsHeaderMetaInfo.ON_IMAGE,
        modifier,
        enter = fadeIn(tween(240)), exit = fadeOut(tween(120)),
    ) {
        val story = controller.story
        val upvoted = controller.isUpvoted(story.id, story.isComment)
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ImageMetric(
                icon = if (upvoted) Res.drawable.ic_thumb_up_filled else Res.drawable.ic_thumb_up,
                count = story.score,
                description = "${story.score} points",
                settings = settings, hazeState = hazeState, textStyle = textStyle,
                selected = upvoted, loading = controller.storyVoteLoading,
            )
            ImageMetric(
                icon = Res.drawable.ic_comment, count = story.descendants,
                description = "${story.descendants} comments",
                settings = settings, hazeState = hazeState, textStyle = textStyle,
            )
        }
    }
}

@Composable
private fun ImageMetric(
    icon: DrawableResource,
    count: Int,
    description: String,
    settings: CommentDisplaySettings,
    hazeState: HazeState,
    textStyle: TextStyle,
    selected: Boolean = false,
    loading: Boolean = false,
) {
    val typography = rememberContentTypography(settings.font)
    Box(
        Modifier.widthIn(min = 48.dp, max = 50.dp)
            .semantics {
                contentDescription = description
                stateDescription = count.toString()
                this.selected = selected
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier.fillMaxWidth().storyImageMetricBackground(hazeState)
                .heightIn(min = 28.dp).padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(icon to loading,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "image metric state") { (visibleIcon, busy) ->
                if (busy) HarmonicLoadingIndicator(Modifier.size(14.dp))
                else Icon(painterResource(visibleIcon), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(compactStoryMetric(count), Modifier.weight(1f, fill = false),
                color = MaterialTheme.colorScheme.onSurface, fontFamily = typography.family,
                fontWeight = FontWeight.Medium, fontSize = 12.sp, style = textStyle,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
