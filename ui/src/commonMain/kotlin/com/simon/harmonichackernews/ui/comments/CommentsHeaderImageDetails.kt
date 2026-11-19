package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
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
    stacked: Boolean = false,
    animateSettingsPreview: Boolean = false,
) {
    AnimatedVisibility(
        settings.headerMetaInfo == CommentsHeaderMetaInfo.ON_IMAGE,
        modifier,
        enter = fadeIn(tween(240)), exit = fadeOut(tween(120)),
    ) {
        val metricsModifier = Modifier.padding(4.dp).testTag("header-image-metrics")
        val metrics: @Composable () -> Unit = {
            HeaderMetricItems(
                controller, settings, textStyle, hazeState = hazeState, vertical = stacked,
                animateSettingsPreview = animateSettingsPreview,
            )
        }
        if (stacked) {
            Column(
                metricsModifier,
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(if (animateSettingsPreview) 0.dp else 4.dp),
                content = { metrics() },
            )
        } else {
            Row(
                metricsModifier,
                horizontalArrangement = Arrangement.spacedBy(if (animateSettingsPreview) 0.dp else 4.dp),
                content = { metrics() },
            )
        }
    }
}

/** The same badges form a compact rail beside the title while no image is available. */
@Composable
internal fun HeaderStackedMetrics(
    controller: CommentsScreenController,
    settings: CommentDisplaySettings,
    textStyle: TextStyle,
    headerColor: Color,
    visible: Boolean,
    animateSettingsPreview: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(240, delayMillis = 80)) +
            expandHorizontally(tween(320, easing = FastOutSlowInEasing), expandFrom = Alignment.End),
        exit = fadeOut(tween(120)) +
            shrinkHorizontally(tween(320, easing = FastOutSlowInEasing), shrinkTowards = Alignment.End),
    ) {
        Column(
            Modifier.padding(end = 16.dp).testTag("header-stacked-metrics"),
            verticalArrangement = Arrangement.spacedBy(if (animateSettingsPreview) 0.dp else 4.dp),
        ) {
            HeaderMetricItems(controller, settings, textStyle, headerColor = headerColor,
                animateSettingsPreview = animateSettingsPreview)
        }
    }
}

@Composable
internal fun HeaderPeekMetrics(
    controller: CommentsScreenController,
    settings: CommentDisplaySettings,
    headerColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        HeaderMetricItems(controller, settings, TextStyle.Default, headerColor = headerColor, compact = true)
    }
}

@Composable
private fun HeaderMetricItems(
    controller: CommentsScreenController,
    settings: CommentDisplaySettings,
    textStyle: TextStyle,
    hazeState: HazeState? = null,
    headerColor: Color = Color.Unspecified,
    animateSettingsPreview: Boolean = false,
    compact: Boolean = false,
    vertical: Boolean = hazeState == null,
) {
    val story = controller.story
    val upvoted = controller.isUpvoted(story.id, story.isComment)
    HeaderSettingsVisibility(
        visible = !story.isComment && settings.showHeaderPoints,
        animate = animateSettingsPreview,
        animateSize = hazeState == null,
        vertical = vertical,
        clip = false,
    ) { modifier ->
        ImageMetric(
            icon = if (upvoted) Res.drawable.ic_thumb_up_filled else Res.drawable.ic_thumb_up,
            count = story.score,
            description = "${story.score} points",
            settings = settings, hazeState = hazeState, textStyle = textStyle,
            selected = upvoted, loading = controller.storyVoteLoading,
            headerColor = headerColor,
            compact = compact,
            // Keep the badge and its spacing intact throughout the fade.
            modifier = modifier.then(if (animateSettingsPreview) {
                if (vertical) Modifier.padding(bottom = 4.dp) else Modifier.padding(end = 4.dp)
            } else Modifier),
        )
    }
    val commented = controller.storyCommented
    ImageMetric(
        icon = if (commented) Res.drawable.ic_comment_filled else Res.drawable.ic_comment,
        count = story.descendants,
        description = "${story.descendants} comments" + if (commented) ", you commented" else "",
        settings = settings, hazeState = hazeState, textStyle = textStyle,
        headerColor = headerColor,
        highlighted = commented,
        compact = compact,
    )
}

@Composable
private fun ImageMetric(
    icon: DrawableResource,
    count: Int,
    description: String,
    settings: CommentDisplaySettings,
    hazeState: HazeState?,
    textStyle: TextStyle,
    headerColor: Color,
    selected: Boolean = false,
    loading: Boolean = false,
    /** Tints the icon with the accent color, e.g. for stories the user has commented on. */
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val typography = rememberContentTypography(settings.font)
    val onImage = hazeState != null
    Box(
        modifier.then(if (onImage) Modifier else Modifier.widthIn(min = if (compact) 48.dp else 56.dp))
            .semantics {
                contentDescription = description
                stateDescription = count.toString()
                this.selected = selected
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier.then(
                if (hazeState != null) Modifier.storyImageMetricBackground(hazeState)
                else Modifier.background(
                    lerp(headerColor, MaterialTheme.colorScheme.onSurface, 0.07f), RoundedCornerShape(10.dp),
                )
            ).heightIn(min = if (compact) 22.dp else if (onImage) 28.dp else 32.dp)
                .padding(horizontal = if (compact) 4.dp else 8.dp, vertical = if (compact) 2.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(icon to loading,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "image metric state") { (visibleIcon, busy) ->
                if (busy) HarmonicLoadingIndicator(Modifier.size(14.dp))
                else Icon(
                    painterResource(visibleIcon), null, Modifier.size(14.dp),
                    tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(compactStoryMetric(count),
                color = MaterialTheme.colorScheme.onSurface, fontFamily = typography.family,
                fontWeight = FontWeight.Medium, fontSize = if (compact) 11.sp else 12.sp,
                lineHeight = if (compact) 14.sp else androidx.compose.ui.unit.TextUnit.Unspecified, style = textStyle,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
