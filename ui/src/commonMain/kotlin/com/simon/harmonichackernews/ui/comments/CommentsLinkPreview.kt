package com.simon.harmonichackernews.ui.comments

import org.jetbrains.compose.resources.DrawableResource


import com.simon.harmonichackernews.resources.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.ui.common.HarmonicLoadingIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.simon.harmonichackernews.data.LinkPreviewType

data class CommentsPreviewPlatform(
    val textStyle: TextStyle,
    val openLink: (String?) -> Unit,
    val downloadPdf: (String?) -> Unit,
    val openCustomTab: (String?) -> Unit,
    val plainText: (String) -> String,
    val annotatedHtml: (String, TextLinkStyles, LinkInteractionListener) -> AnnotatedString,
)

val LocalCommentsPreviewPlatform = staticCompositionLocalOf<CommentsPreviewPlatform> {
    error("CommentsPreviewPlatform was not provided")
}

@Composable
fun CommentsPreviewPlatformProvider(
    platform: CommentsPreviewPlatform,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalCommentsPreviewPlatform provides platform, content = content)
}

@Composable
fun LinkPreviewContent(
    story: StoryListItemSnapshot,
    contentVersion: Int,
    settings: CommentDisplaySettings,
) {
    val previewType = remember(story, contentVersion) { story.loadedLinkPreviewType() }
    AnimatedVisibility(
        visible = previewType != null || story.linkPreviewLoading,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        val colors = HarmonicTheme.colors
        AnimatedContent(
            targetState = previewType,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, colors.mutedText, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            transitionSpec = {
                (fadeIn(tween(160)) togetherWith fadeOut(tween(160))).using(
                    SizeTransform(clip = false) { _, _ -> tween(220) },
                )
            },
            label = "comments link preview",
        ) {
            when (it) {
                LinkPreviewType.GITHUB_REPOSITORY -> GitHubPreview(story)
                LinkPreviewType.GITLAB_PROJECT -> GitLabPreview(story)
                LinkPreviewType.HUGGING_FACE_MODEL -> HuggingFacePreview(story)
                LinkPreviewType.OPENROUTER_MODEL -> OpenRouterPreview(story)
                LinkPreviewType.STACK_EXCHANGE -> StackExchangePreview(story)
                LinkPreviewType.ARXIV -> ArxivPreview(story, settings)
                LinkPreviewType.WIKIPEDIA -> WikipediaPreview(story)
                LinkPreviewType.TWITTER_X -> NitterPreview(story)
                null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    HarmonicLoadingIndicator(Modifier.size(44.dp))
                }
                else -> RichLinkPreview(story)
            }
        }
    }
}

@Composable
internal fun PreviewHeader(
    text: String,
    icon: DrawableResource? = null,
    logoUrl: String? = null,
    logoTint: Color? = null,
    tintIcon: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            val fallback = if (tintIcon) {
                tintedPainterResource(icon, HarmonicTheme.colors.iconTint)
            } else {
                painterResource(icon)
            }
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = null,
                    placeholder = fallback,
                    fallback = fallback,
                    error = fallback,
                    colorFilter = logoTint?.let(ColorFilter::tint),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(5.dp)),
                )
            } else {
                Icon(
                    painter = fallback,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Unspecified,
                )
            }
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text.uppercase(),
            color = HarmonicTheme.colors.contentPrimary,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            style = LocalCommentsPreviewPlatform.current.textStyle,
        )
    }
}

@Composable
internal fun PreviewBody(
    text: String,
    bold: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    topPadding: Dp = 6.dp,
    bottomPadding: Dp = 4.dp,
    fontFamily: FontFamily = ProductSansFontFamily,
    fontSize: Float = 14f,
    lineHeight: Float = 17f,
) {
    if (text.isBlank()) return
    Text(
        text = text,
        modifier = Modifier.padding(top = topPadding, bottom = bottomPadding),
        color = HarmonicTheme.colors.contentPrimary,
        fontFamily = fontFamily,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = fontSize.sp,
        lineHeight = lineHeight.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = LocalCommentsPreviewPlatform.current.textStyle,
    )
}

@Composable
internal fun PreviewInfoRow(
    icon: DrawableResource,
    text: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    if (text.isNullOrBlank()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 22.dp)
            .then(
                if (onClick != null) {
                    Modifier.clip(RoundedCornerShape(2.dp))
                        .combinedClickable(onClick = onClick, onLongClick = null)
                } else {
                    Modifier
                },
            )
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .padding(end = 4.dp)
                .size(20.dp),
            tint = HarmonicTheme.colors.iconTint,
        )
        Text(
            text,
            color = if (onClick != null) HarmonicTheme.colors.link else HarmonicTheme.colors.contentPrimary,
            fontFamily = ProductSansFontFamily,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = LocalCommentsPreviewPlatform.current.textStyle,
        )
    }
}

@Composable
internal fun PreviewInfoColumns(left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(2f), content = left)
        Column(Modifier.weight(3f), content = right)
    }
}
