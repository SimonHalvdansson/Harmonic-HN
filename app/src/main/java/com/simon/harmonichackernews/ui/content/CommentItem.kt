package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.ThemeUtils

data class CommentItemUiModel(
    val author: String,
    val age: String,
    val body: String,
    val referenceMarker: String,
    val referenceUrl: String,
)

data class CommentItemStyle(
    val cardStyle: Boolean,
    val showCardBorder: Boolean,
    val textSize: Float,
    val collectLinks: Boolean,
    val emphasizeMeta: Boolean,
    val depthIndicatorMode: String,
    val showDivider: Boolean,
    val preferredFont: String,
)

val SettingsCommentPreviewModel = CommentItemUiModel(
    author = "pg",
    age = "1h",
    body = "This reminds me of the old systems where the boring path was often the most durable one. " +
        "The less hidden state there is, the easier it is to reason about. [0]",
    referenceMarker = "[0]",
    referenceUrl = "https://example.com/reference",
)

@Composable
fun CommentItem(
    model: CommentItemUiModel,
    style: CommentItemStyle,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = HarmonicTheme.colors
    val typography = rememberContentTypography(
        preferredFont = style.preferredFont,
        commentTextSize = style.textSize,
    )
    val bodySize by animateFloatAsState(
        targetValue = typography.commentTextSize,
        animationSpec = contentTween(),
        label = "comment text size",
    )
    val referenceLabelSize by animateFloatAsState(
        targetValue = typography.referenceLabelSize,
        animationSpec = contentTween(),
        label = "comment reference size",
    )
    val shadowPadding by animateDpAsState(
        targetValue = if (style.cardStyle) 4.dp else 0.dp,
        animationSpec = contentTween(),
        label = "comment card shadow padding",
    )
    val background by animateColorAsState(
        targetValue = colors.surfaceContainerHigh.copy(
            alpha = if (style.cardStyle) 1f else 0f,
        ),
        animationSpec = contentTween(),
        label = "comment card background",
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (style.cardStyle) 3.5.dp else 2.5.dp,
        animationSpec = contentTween(),
        label = "comment indicator width",
    )
    val indicatorEndMargin by animateDpAsState(
        targetValue = if (style.cardStyle) 4.dp else 8.dp,
        animationSpec = contentTween(),
        label = "comment indicator margin",
    )
    val contentStartPadding by animateDpAsState(
        targetValue = if (style.cardStyle) 4.5.dp else 5.dp,
        animationSpec = contentTween(),
        label = "comment start padding",
    )
    val contentEndPadding by animateDpAsState(
        targetValue = if (style.cardStyle) 8.dp else 4.dp,
        animationSpec = contentTween(),
        label = "comment end padding",
    )
    val contentVerticalPadding by animateDpAsState(
        targetValue = if (style.cardStyle) 7.dp else 5.dp,
        animationSpec = contentTween(),
        label = "comment vertical padding",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (style.cardStyle && style.showCardBorder) 1f else 0f,
        animationSpec = contentTween(),
        label = "comment card border",
    )
    val dividerInset by animateDpAsState(
        targetValue = if (style.cardStyle) 8.dp else 4.dp,
        animationSpec = contentTween(),
        label = "comment divider inset",
    )
    val dividerHeight by animateDpAsState(
        targetValue = if (style.showDivider) 4.dp else 0.dp,
        animationSpec = contentTween(),
        label = "comment divider height",
    )
    val dividerAlpha by animateFloatAsState(
        targetValue = if (style.showDivider) 1f else 0f,
        animationSpec = contentTween(),
        label = "comment divider alpha",
    )
    val depthColorRes = CommentDepthIndicatorUtils.getColorResource(
        context,
        style.depthIndicatorMode,
        ThemeUtils.getPreferredTheme(context),
        0,
    )
    val rawIndicatorColor = Color(ContextCompat.getColor(context, depthColorRes))
    val indicatorColor by animateColorAsState(
        targetValue = rawIndicatorColor,
        animationSpec = contentTween(),
        label = "comment indicator color",
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (CommentDepthIndicatorUtils.shouldShowIndicators(style.depthIndicatorMode)) {
            1f
        } else {
            0f
        },
        animationSpec = contentTween(),
        label = "comment indicator visibility",
    )
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(shadowPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .shadow(
                        elevation = 1.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = borderAlpha),
                        spotColor = Color.Black.copy(alpha = borderAlpha),
                    )
                    .clip(shape)
                    .background(background)
                    .border(
                        width = 1.dp,
                        color = colors.commentDivider.copy(alpha = borderAlpha),
                        shape = shape,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .width(indicatorWidth)
                        .fillMaxHeight()
                        .graphicsLayer(alpha = indicatorAlpha)
                        .background(indicatorColor),
                )
                Box(modifier = Modifier.width(indicatorEndMargin))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            start = contentStartPadding,
                            top = contentVerticalPadding,
                            end = contentEndPadding,
                            bottom = contentVerticalPadding,
                        ),
                ) {
                    CommentMeta(
                        model = model,
                        style = style,
                        typography = typography,
                    )
                    Text(
                        text = model.body,
                        color = colors.storyNormal,
                        fontFamily = typography.family,
                        fontSize = bodySize.sp,
                        style = animatedCommentTextStyle,
                    )
                    CommentReference(
                        model = model,
                        collectLinks = style.collectLinks,
                        typography = typography,
                        bodySize = bodySize,
                        referenceLabelSize = referenceLabelSize,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dividerHeight)
                .padding(horizontal = dividerInset),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .graphicsLayer(alpha = dividerAlpha)
                    .background(colors.commentDivider),
            )
        }
    }
}

@Composable
private fun CommentMeta(
    model: CommentItemUiModel,
    style: CommentItemStyle,
    typography: ContentTypography,
) {
    val colors = HarmonicTheme.colors
    val horizontalPadding by animateDpAsState(
        targetValue = if (style.emphasizeMeta) 7.dp else 0.dp,
        animationSpec = contentTween(),
        label = "comment meta horizontal padding",
    )
    val verticalPadding by animateDpAsState(
        targetValue = if (style.emphasizeMeta) 2.dp else 0.dp,
        animationSpec = contentTween(),
        label = "comment meta vertical padding",
    )
    val background by animateColorAsState(
        targetValue = colors.surfaceContainerHighest.copy(
            alpha = if (style.emphasizeMeta) 1f else 0f,
        ),
        animationSpec = contentTween(),
        label = "comment meta background",
    )
    val textColor by animateColorAsState(
        targetValue = if (style.emphasizeMeta) colors.storyNormal else colors.storyDisabled,
        animationSpec = contentTween(),
        label = "comment meta text color",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (style.emphasizeMeta) 1f else 0f,
        animationSpec = contentTween(),
        label = "comment meta border",
    )
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .padding(bottom = 2.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, colors.commentDivider.copy(alpha = borderAlpha), shape)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = model.author,
            color = textColor,
            fontFamily = typography.family,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            style = commentTextStyle,
        )
        Text(
            text = model.age,
            modifier = Modifier.padding(start = 4.dp),
            color = textColor,
            fontFamily = typography.family,
            fontSize = 13.sp,
            style = commentTextStyle,
        )
    }
}

@Composable
private fun CommentReference(
    model: CommentItemUiModel,
    collectLinks: Boolean,
    typography: ContentTypography,
    bodySize: Float,
    referenceLabelSize: Float,
) {
    AnimatedContent(
        targetState = collectLinks,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp),
        transitionSpec = {
            (fadeIn(contentTween()) + slideInVertically(contentTween()) { it / 4 })
                .togetherWith(
                    fadeOut(contentTween()) + slideOutVertically(contentTween()) { -it / 4 },
                )
                .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> contentTween() }))
        },
        label = "comment collected links",
    ) { collected ->
        if (collected) {
            CollectedReferenceRow(
                model = model,
                typography = typography,
                labelSize = referenceLabelSize,
            )
        } else {
            Text(
                text = buildAnnotatedString {
                    append(model.referenceMarker)
                    append(' ')
                    pushStyle(
                        SpanStyle(
                            color = HarmonicTheme.colors.link,
                            textDecoration = TextDecoration.Underline,
                        ),
                    )
                    append(model.referenceUrl)
                    pop()
                },
                color = HarmonicTheme.colors.storyNormal,
                fontFamily = typography.family,
                fontSize = bodySize.sp,
                style = animatedCommentTextStyle,
            )
        }
    }
}

@Composable
private fun CollectedReferenceRow(
    model: CommentItemUiModel,
    typography: ContentTypography,
    labelSize: Float,
) {
    val colors = HarmonicTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .defaultMinSize(minHeight = 38.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, colors.commentDivider, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_public),
            contentDescription = null,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(17.dp),
        )
        Text(
            text = model.referenceMarker,
            modifier = Modifier.padding(end = 8.dp),
            color = colors.storyDisabled,
            fontFamily = typography.family,
            fontWeight = FontWeight.Bold,
            fontSize = typography.referenceMarkerSize.sp,
            style = compactCommentTextStyle,
        )
        Text(
            text = model.referenceUrl,
            modifier = Modifier.weight(1f),
            color = colors.storyNormal,
            fontFamily = typography.family,
            fontSize = labelSize.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = compactAnimatedCommentTextStyle,
        )
    }
}

private val commentTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = true),
)

private val animatedCommentTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = true),
    textMotion = TextMotion.Animated,
)

private val compactCommentTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val compactAnimatedCommentTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    textMotion = TextMotion.Animated,
)

@Preview(widthDp = 412, showBackground = true)
@Composable
private fun CommentItemPreview() {
    HarmonicTheme {
        CommentItem(
            model = SettingsCommentPreviewModel,
            style = CommentItemStyle(
                cardStyle = false,
                showCardBorder = true,
                textSize = SettingsUtils.DEFAULT_COMMENT_TEXT_SIZE,
                collectLinks = true,
                emphasizeMeta = false,
                depthIndicatorMode = CommentDepthIndicatorUtils.MODE_THEME_DEFAULT,
                showDivider = false,
                preferredFont = "googlesansflexrounded",
            ),
        )
    }
}
