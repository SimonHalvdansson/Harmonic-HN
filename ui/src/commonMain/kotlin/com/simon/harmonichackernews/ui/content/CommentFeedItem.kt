package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.ui.common.TextButton
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.HtmlTextUtils
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** The same comment card is used in saved feeds and user submissions. */
@Composable
fun CommentFeedItem(
    commentMasterTitle: String?,
    timeText: String,
    html: String,
    canOpenStory: Boolean,
    displaySettings: StoryDisplaySettings,
    onOpenLink: (String) -> Unit,
    onStoryClick: () -> Unit,
    onRepliesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HarmonicTheme.colors
    // Relative times normally have no descenders; years and "just now" do.
    // Center the visible Product Sans glyphs, with a font-scaled optical correction.
    val timeOpticalOffset = with(LocalDensity.current) {
        if (timeText.any { it in "gjpqy" }) (-1).sp.toDp() else 0.dp
    }
    val cardStyle = displaySettings.cardStyle
    val cardBackground = if (displaySettings.hasBackground) {
        colors.storyCardBackground
    } else {
        colors.settingsPageBackground
    }
    val shape = RoundedCornerShape(8.dp)
    val container = modifier
        .fillMaxWidth()
        .padding(
            horizontal = 8.dp,
            vertical = 4.dp,
        )
        .shadow(if (cardStyle) 1.dp else 0.dp, shape, clip = false)
        .clip(shape)
        .background(cardBackground)
        .border(
            1.dp,
            if (cardStyle) colors.outlineVariant else Color.Transparent,
            shape,
        )

    Column(
        modifier = container.padding(
            start = 16.dp,
            top = 10.dp,
            end = 16.dp,
            bottom = 4.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (commentMasterTitle.isNullOrBlank()) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "On",
                        color = colors.storyDisabled,
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        style = legacyTextStyle,
                    )
                    CommentStoryTitleShimmer(
                        Modifier.padding(start = 6.dp).weight(1f).height(17.dp),
                    )
                }
            } else {
                Text(
                    text = "On \"$commentMasterTitle\"",
                    modifier = Modifier.weight(1f),
                    color = colors.storyDisabled,
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    style = legacyTextStyle,
                )
            }
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .defaultMinSize(minHeight = 22.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.submissionsCommentTimeBackground)
                    .border(
                        1.dp,
                        colors.submissionsCommentTimeOutline,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = timeText,
                    modifier = Modifier.offset(y = timeOpticalOffset),
                    color = colors.storyDisabled,
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    style = legacyTextStyle,
                )
            }
        }

        CommentFeedBody(
            html = html,
            preferredFont = displaySettings.font,
            textSize = displaySettings.commentTextSize,
            background = cardBackground,
            onOpenLink = onOpenLink,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        ) {
            CommentFeedActionButton(
                label = "Story",
                icon = Res.drawable.ic_newspaper,
                onClick = onStoryClick,
                enabled = canOpenStory,
            )
            CommentFeedActionButton(
                label = "Replies",
                icon = Res.drawable.ic_reply,
                onClick = onRepliesClick,
            )
        }
    }
}

@Composable
private fun CommentFeedBody(
    html: String,
    preferredFont: String,
    textSize: Float,
    background: Color,
    onOpenLink: (String) -> Unit,
) {
    val linkColor = HarmonicTheme.colors.link
    val linkListener = remember(onOpenLink) {
        LinkInteractionListener { annotation ->
            if (annotation is LinkAnnotation.Url) {
                onOpenLink(annotation.url)
            }
        }
    }
    val formatted = remember(html, linkColor, linkListener) {
        htmlAnnotatedString(
            HtmlTextUtils.expandShortenedAnchorText(html).orEmpty(),
            linkColor,
            linkListener,
        )
    }
    val typography = com.simon.harmonichackernews.ui.content.rememberContentTypography(
        preferredFont = preferredFont,
        commentTextSize = textSize,
    )
    var truncated by remember(formatted) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = formatted,
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = typography.family,
            fontSize = typography.commentTextSize.sp,
            maxLines = 16,
            overflow = TextOverflow.Ellipsis,
            style = legacyTextStyle,
            onTextLayout = { truncated = it.hasVisualOverflow },
        )
        if (truncated) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, background),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun CommentFeedActionButton(
    label: String,
    icon: DrawableResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = HarmonicTheme.colors.accent),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun CommentStoryTitleShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "comment story loading")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing)),
        label = "comment story shimmer",
    )
    val base = HarmonicTheme.colors.surfaceContainerHighest
    val highlight = HarmonicTheme.colors.storyNormal.copy(alpha = 0.12f)
    Box(modifier.widthIn(max = 150.dp).clip(RoundedCornerShape(5.dp)).drawWithCache {
        onDrawBehind {
            drawRect(base)
            val x = size.width * progress
            drawRect(Brush.linearGradient(
                listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(x - size.width * 0.5f, 0f),
                end = Offset(x + size.width * 0.5f, size.height),
            ))
        }
    })
}

private val legacyTextStyle = TextStyle()
