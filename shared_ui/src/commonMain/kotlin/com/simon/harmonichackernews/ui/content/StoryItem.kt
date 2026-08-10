package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_comment
import com.simon.harmonichackernews.resources.ic_image
import com.simon.harmonichackernews.resources.ic_public
import com.simon.harmonichackernews.resources.ic_whatshot
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private const val ContentAnimationDuration = 220
private val ContentMotionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Immutable
data class StoryItemUiModel(
    val index: String,
    val title: String,
    val summary: String,
    val points: Int,
    val domain: String,
    val domainWithoutTopLevel: String,
    val age: String,
    val commentCount: Int,
    val faviconFallback: DrawableResource = Res.drawable.ic_public,
    val previewImageFallback: DrawableResource? = null,
    val faviconUrl: String? = null,
    val previewImageUrl: String? = null,
    val previewImageLoadFailed: Boolean = false,
    val faviconTintArgb: Int? = null,
    val previewImageTintArgb: Int? = null,
)

@Immutable
data class StoryItemStyle(
    val previewImageMode: String,
    val borderlessLargeImage: Boolean,
    val compact: Boolean,
    val showSummary: Boolean,
    val showFavicon: Boolean,
    val showPoints: Boolean,
    val compactPoints: Boolean,
    val includeTopLevelDomain: Boolean,
    val showCommentCount: Boolean,
    val showIndex: Boolean,
    val commentsOnLeft: Boolean,
    val tintCard: Boolean,
    val cardStyle: Boolean,
    val useHotnessIcon: Boolean,
    val preferredFont: String,
    val textSize: Float,
    val dimmed: Boolean = false,
    val paletteTintConfigKey: String = "default",
)

val SettingsStoryPreviewModel = StoryItemUiModel(
    index = "3.",
    title = "Algorithm breaks speed limit for solving linear equations",
    summary = "A faster method uses a new approach to solve large linear systems more efficiently.",
    points = 53,
    domain = "science.org",
    domainWithoutTopLevel = "science",
    age = "2h",
    commentCount = 18,
    faviconFallback = Res.drawable.ic_public,
    previewImageFallback = Res.drawable.ic_image,
)

/**
 * Complete platform-neutral story row. Platforms provide URLs and already-resolved palette tints;
 * image fetching is handled by Coil's multiplatform Compose integration.
 */
@Composable
fun StoryItem(
    model: StoryItemUiModel,
    style: StoryItemStyle,
    modifier: Modifier = Modifier,
    listItem: Boolean = false,
    onLinkClick: (() -> Unit)? = null,
    onLinkLongClick: (() -> Unit)? = null,
    onCommentClick: (() -> Unit)? = null,
    onBoundsChanged: ((Rect) -> Unit)? = null,
    onPreviewLoadFailed: (() -> Unit)? = null,
) {
    val colors = HarmonicTheme.colors
    val typography = rememberContentTypography(
        preferredFont = style.preferredFont,
        storyTextSize = style.textSize,
    )
    val animate = !listItem
    val dimAlpha by animateFloatAsState(
        targetValue = if (style.dimmed) 0.6f else 1f,
        animationSpec = tween(if (animate) 180 else 0),
        label = "story dim alpha",
    )
    val cardPadding by animateDpAsState(
        targetValue = if (style.cardStyle) 4.dp else 0.dp,
        animationSpec = tween(if (animate) ContentAnimationDuration else 0),
        label = "story card padding",
    )
    val cardShape = RoundedCornerShape(if (style.cardStyle) 8.dp else 0.dp)
    val tint = model.previewImageTintArgb?.let(::Color)
    val background = when {
        style.tintCard && tint != null -> tint
        style.cardStyle -> colors.surfaceContainerHigh
        else -> colors.background
    }
    var previewFailed by remember(model.previewImageUrl, model.previewImageLoadFailed) {
        mutableStateOf(model.previewImageLoadFailed)
    }
    val hasPreview = !previewFailed &&
        (model.previewImageUrl != null || model.previewImageFallback != null)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = cardPadding)
            .onGloballyPositioned { onBoundsChanged?.invoke(it.boundsInWindow()) }
            .shadow(
                elevation = if (style.cardStyle) 1.dp else 0.dp,
                shape = cardShape,
                clip = false,
            )
            .clip(cardShape)
            .background(background)
            .border(
                width = if (style.cardStyle) 1.dp else 0.dp,
                color = colors.outlineVariant.copy(alpha = if (style.cardStyle) 1f else 0f),
                shape = cardShape,
            ),
    ) {
        Column {
            if (style.previewImageMode == "large" && hasPreview) {
                StoryPreviewImage(
                    model = model,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .then(
                            if (style.borderlessLargeImage) Modifier
                            else Modifier.padding(start = 10.dp, top = 10.dp, end = 10.dp),
                        )
                        .clip(RoundedCornerShape(if (style.borderlessLargeImage) 0.dp else 8.dp))
                        .graphicsLayer(alpha = dimAlpha),
                    onLoadFailed = {
                        previewFailed = true
                        onPreviewLoadFailed?.invoke()
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val comments: @Composable () -> Unit = {
                    StoryCommentRail(
                        model = model,
                        style = style,
                        fontFamily = typography.family,
                        onClick = onCommentClick,
                    )
                }
                if (style.commentsOnLeft) comments()
                StoryMainContent(
                    model = model,
                    style = style,
                    typography = typography,
                    hasSmallPreview = hasPreview && style.previewImageMode == "small",
                    dimAlpha = dimAlpha,
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick,
                    onPreviewLoadFailed = {
                        previewFailed = true
                        onPreviewLoadFailed?.invoke()
                    },
                    modifier = Modifier.weight(1f),
                )
                if (!style.commentsOnLeft) comments()
            }
        }
    }
}

@Composable
private fun StoryMainContent(
    model: StoryItemUiModel,
    style: StoryItemStyle,
    typography: ContentTypography,
    hasSmallPreview: Boolean,
    dimAlpha: Float,
    onLinkClick: (() -> Unit)?,
    onLinkLongClick: (() -> Unit)?,
    onPreviewLoadFailed: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier
            .combinedClickable(
                enabled = onLinkClick != null || onLinkLongClick != null,
                onClick = { onLinkClick?.invoke() },
                onLongClick = { onLinkLongClick?.invoke() },
            )
            .padding(start = 6.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AnimatedVisibility(style.showIndex, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = model.index,
                modifier = Modifier.width(38.dp),
                color = if (style.dimmed) HarmonicTheme.colors.storyDisabled
                else HarmonicTheme.colors.storyNormal,
                fontFamily = typography.family,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                style = legacyTextStyle,
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp, end = 4.dp)) {
            Text(
                text = model.title,
                color = if (style.dimmed) HarmonicTheme.colors.storyDisabled
                else HarmonicTheme.colors.storyNormal,
                fontFamily = typography.family,
                fontWeight = FontWeight.Bold,
                fontSize = typography.storyTitleSize.sp,
                style = legacyTextStyle,
            )
            AnimatedVisibility(style.showSummary && model.summary.isNotBlank()) {
                Text(
                    text = model.summary,
                    modifier = Modifier.padding(top = 3.dp),
                    color = HarmonicTheme.colors.storyDisabled,
                    fontFamily = typography.family,
                    fontSize = typography.storySummarySize.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = legacyTextStyle,
                )
            }
            if (!style.compact) {
                StoryMeta(
                    model = model,
                    style = style,
                    typography = typography,
                    dimAlpha = dimAlpha,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        if (hasSmallPreview) {
            StoryPreviewImage(
                model = model,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(width = 72.dp, height = 52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .graphicsLayer(alpha = dimAlpha),
                onLoadFailed = onPreviewLoadFailed,
            )
        }
    }
}

@Composable
private fun StoryPreviewImage(
    model: StoryItemUiModel,
    modifier: Modifier,
    onLoadFailed: () -> Unit,
) {
    if (model.previewImageUrl != null) {
        AsyncImage(
            model = model.previewImageUrl,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            onError = { onLoadFailed() },
        )
    } else {
        model.previewImageFallback?.let {
            Image(
                painter = painterResource(it),
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun StoryMeta(
    model: StoryItemUiModel,
    style: StoryItemStyle,
    typography: ContentTypography,
    dimAlpha: Float,
    modifier: Modifier,
) {
    val metaText = remember(model, style.showPoints, style.compactPoints, style.includeTopLevelDomain) {
        buildString {
            if (style.showPoints) {
                append(if (style.compactPoints) "+${model.points}" else "${model.points} points")
                append(" • ")
            }
            append(if (style.includeTopLevelDomain) model.domain else model.domainWithoutTopLevel)
            append(" • ${model.age}")
        }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(style.showFavicon) {
            if (model.faviconUrl != null) {
                AsyncImage(
                    model = model.faviconUrl,
                    placeholder = painterResource(model.faviconFallback),
                    fallback = painterResource(model.faviconFallback),
                    error = painterResource(model.faviconFallback),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(17.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .graphicsLayer(alpha = dimAlpha),
                )
            } else {
                Icon(
                    painter = painterResource(model.faviconFallback),
                    contentDescription = null,
                    tint = model.faviconTintArgb?.let(::Color) ?: HarmonicTheme.colors.drawable,
                    modifier = Modifier.padding(end = 4.dp).size(17.dp),
                )
            }
        }
        AnimatedContent(metaText, transitionSpec = { fadeIn().togetherWith(fadeOut()) }) { text ->
            Text(
                text = text,
                color = HarmonicTheme.colors.storyDisabled,
                fontFamily = typography.family,
                fontSize = typography.storyMetaSize.sp,
                style = legacyTextStyle,
            )
        }
    }
}

@Composable
private fun StoryCommentRail(
    model: StoryItemUiModel,
    style: StoryItemStyle,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    onClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .width(60.dp)
            .fillMaxHeight()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(
                if (style.useHotnessIcon) Res.drawable.ic_whatshot else Res.drawable.ic_comment,
            ),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = HarmonicTheme.colors.drawable.copy(alpha = if (style.dimmed) 0.6f else 1f),
        )
        AnimatedVisibility(style.showCommentCount && !style.compact) {
            Text(
                text = model.commentCount.toString(),
                color = if (style.dimmed) HarmonicTheme.colors.storyDisabled
                else HarmonicTheme.colors.storyNormal,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                style = legacyTextStyle,
            )
        }
    }
}

fun <T> contentTween() = tween<T>(
    durationMillis = ContentAnimationDuration,
    easing = ContentMotionEasing,
)

private val legacyTextStyle = TextStyle(
    textMotion = TextMotion.Static,
)
