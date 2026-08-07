package com.simon.harmonichackernews.ui.content

import android.text.Html
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.android.material.color.MaterialColors
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.network.FaviconLoader
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.CollectedReferenceLinks
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils
import com.simon.harmonichackernews.utils.ReferenceLinkRowUtils
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.ThemeUtils
import com.simon.harmonichackernews.utils.Utils
import java.util.Locale
import kotlin.math.min

@Immutable
data class CommentItemUiModel(
    val author: String,
    val age: String,
    val body: String,
    val referenceMarker: String,
    val referenceUrl: String,
)

@Immutable
data class CommentItemStyle(
    val cardStyle: Boolean,
    val showCardBorder: Boolean,
    val textSize: Float,
    val collectLinks: Boolean,
    val emphasizeMeta: Boolean,
    val depthIndicatorMode: String,
    val showDivider: Boolean,
    val preferredFont: String,
    val animateChanges: Boolean = true,
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
        animationSpec = if (style.animateChanges) contentTween() else snap(),
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

/**
 * Runtime comment row used by the Compose comments screen. The preview overload above remains a
 * compact settings model; this overload maps the real Hacker News comment model and preserves the
 * indentation, collapse, collected-reference, tap/long-press and OP/self styling of the legacy
 * RecyclerView item.
 */
@Composable
fun CommentItem(
    comment: Comment,
    style: CommentItemStyle,
    storyAuthor: String?,
    accountUser: String?,
    userTag: String?,
    hiddenReplyCount: Int,
    collapseParent: Boolean,
    showTopLevelIndicator: Boolean,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    flattenHierarchy: Boolean = false,
    forceExpanded: Boolean = false,
    searchTerm: String = "",
    onToggleExpanded: () -> Unit,
    onShowActions: () -> Unit,
    onLinkLongClick: (String, String, Rect) -> Unit,
    onReferenceLongClick: (CollectedReferenceLinks.ReferenceLink, Rect) -> Unit,
) {
    val context = LocalContext.current
    val colors = HarmonicTheme.colors
    val typography = rememberContentTypography(
        preferredFont = style.preferredFont,
        commentTextSize = style.textSize,
    )
    val bodySize by animateFloatAsState(
        targetValue = typography.commentTextSize,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "runtime comment text size",
    )
    val referenceLabelSize by animateFloatAsState(
        targetValue = typography.referenceLabelSize,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "runtime comment reference size",
    )
    val byOp = comment.by == storyAuthor
    val byUser = !accountUser.isNullOrBlank() && comment.by == accountUser
    val metaColor = when {
        byUser -> Color(MaterialColors.getColor(context, R.attr.selfCommentColor, colors.storyDisabled.toArgb()))
        byOp -> Color(MaterialColors.getColor(context, R.attr.opCommentColor, colors.storyDisabled.toArgb()))
        style.emphasizeMeta -> colors.storyNormal
        else -> colors.storyDisabled
    }
    val cardShape = RoundedCornerShape(8.dp)
    val rowShape = if (style.cardStyle) cardShape else RectangleShape
    val effectiveDepth = if (flattenHierarchy) 0 else comment.depth
    val showIndicator = !flattenHierarchy &&
        CommentDepthIndicatorUtils.shouldShowIndicators(style.depthIndicatorMode) &&
        (effectiveDepth > 0 || showTopLevelIndicator)
    val indicatorIndex = (effectiveDepth + if (showTopLevelIndicator) 0 else -1)
        .coerceAtLeast(0) % 7
    val indicatorColor = Color(
        ContextCompat.getColor(
            context,
            CommentDepthIndicatorUtils.getColorResource(
                context,
                style.depthIndicatorMode,
                ThemeUtils.getPreferredTheme(context),
                indicatorIndex,
            ),
        ),
    )
    val referenceLinks = remember(comment.text, style.collectLinks) {
        if (style.collectLinks) {
            CollectedReferenceLinks.parse(comment.expandedAnchorText)
        } else {
            null
        }
    }
    val bodyHtml = if (referenceLinks?.hasLinks() == true) {
        referenceLinks.bodyHtml
    } else {
        comment.expandedAnchorText.orEmpty()
    }
    val linkStyles = remember(colors.link) {
        TextLinkStyles(
            style = SpanStyle(
                color = colors.link,
                textDecoration = TextDecoration.Underline,
            ),
        )
    }
    val linkListener = remember(context) {
        LinkInteractionListener { annotation ->
            if (annotation is LinkAnnotation.Url) {
                Utils.openLinkMaybeHN(context, annotation.url)
            }
        }
    }
    val body = remember(bodyHtml, linkStyles, linkListener) {
        htmlAnnotatedString(bodyHtml, linkStyles, linkListener)
    }
    val markedColor = remember(context) {
        Color(
            if (ThemeUtils.isDarkMode(context)) 0xfffce205.toInt() else 0xffcc7722.toInt(),
        )
    }
    val displayedBody = remember(body, searchTerm, markedColor) {
        val needle = searchTerm.trim()
        if (needle.isEmpty()) {
            body
        } else {
            buildAnnotatedString {
                append(body)
                val haystack = body.text.lowercase(Locale.ROOT)
                val normalizedNeedle = needle.lowercase(Locale.ROOT)
                var start = haystack.indexOf(normalizedNeedle)
                while (start >= 0) {
                    addStyle(
                        SpanStyle(color = markedColor, fontWeight = FontWeight.Bold),
                        start,
                        start + normalizedNeedle.length,
                    )
                    start = haystack.indexOf(normalizedNeedle, start + normalizedNeedle.length)
                }
            }
        }
    }
    val hiddenPreview = remember(comment.text) {
        Html.fromHtml(
            comment.text.orEmpty().take(120),
            Html.FROM_HTML_MODE_LEGACY,
        ).toString().replace('\n', ' ')
    }
    val textCollapsed = !forceExpanded && !comment.expanded && collapseParent
    val defaultBackground = if (style.cardStyle) colors.surfaceContainerHigh else colors.background
    val highlightAlpha = if (defaultBackground.luminance() < 0.5f) 0.14f else 0.08f
    val highlightOverlayAlpha by animateFloatAsState(
        targetValue = if (highlighted) highlightAlpha else 0f,
        animationSpec = contentTween(),
        label = "comment search highlight opacity",
    )
    // Animate only the overlay opacity. Interpolating between Transparent and an opaque
    // composited color also interpolates through Transparent's zero RGB channels, which makes
    // the highlight look much stronger than the intended subtle overlay.
    val commentBackground = colors.storyNormal.copy(alpha = highlightOverlayAlpha)
        .compositeOver(defaultBackground)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val start = min(16.dp.value + 12.dp.value * effectiveDepth, maxWidth.value * 0.6f).dp
        val top = if (style.cardStyle) {
            if (effectiveDepth > 0 && !collapseParent) 2.dp else 0.dp
        } else if (effectiveDepth > 0 && !collapseParent) {
            10.dp
        } else {
            6.dp
        }
        val bottom = if (style.cardStyle) 0.dp else 6.dp
        val shadowPadding = if (style.cardStyle) 4.dp else 0.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (start - shadowPadding).coerceAtLeast(0.dp), end = 16.dp)
                .padding(top = top, bottom = bottom),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(shadowPadding)
                    .shadow(
                        elevation = if (style.cardStyle && style.showCardBorder) 1.dp else 0.dp,
                        shape = rowShape,
                        clip = false,
                    )
                    .clip(rowShape)
                    .background(commentBackground)
                    .border(
                        1.dp,
                        if (style.cardStyle && style.showCardBorder) {
                            colors.commentDivider
                        } else {
                            Color.Transparent
                        },
                        rowShape,
                    )
                    .height(IntrinsicSize.Min)
                    .combinedClickable(
                        onClick = onToggleExpanded,
                        onLongClick = onShowActions,
                    ),
            ) {
                if (showIndicator || style.cardStyle) {
                    Box(
                        modifier = Modifier
                            .width(if (style.cardStyle) 3.5.dp else 2.5.dp)
                            .fillMaxHeight()
                            .background(if (showIndicator) indicatorColor else Color.Transparent),
                    )
                    Box(Modifier.width(if (style.cardStyle) 4.dp else 8.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            start = if (style.cardStyle) 4.5.dp else 5.dp,
                            top = if (style.cardStyle) 7.dp else 5.dp,
                            end = if (style.cardStyle) 8.dp else 4.dp,
                            bottom = if (style.cardStyle) 7.dp else 5.dp,
                        ),
                ) {
                    RuntimeCommentMeta(
                        author = buildString {
                            append(comment.by.orEmpty())
                            if (!userTag.isNullOrBlank()) append(" (").append(userTag).append(')')
                        },
                        age = comment.timeFormatted,
                        byOp = byOp,
                        color = metaColor,
                        emphasized = style.emphasizeMeta,
                        hiddenPreview = if (textCollapsed) " • $hiddenPreview" else null,
                        hiddenReplyCount = hiddenReplyCount.takeIf { it > 0 && !comment.expanded },
                        fontFamily = typography.family,
                        animateChanges = style.animateChanges,
                    )

                    AnimatedVisibility(
                        visible = !textCollapsed && body.isNotEmpty(),
                        enter = if (style.animateChanges) {
                            fadeIn(contentTween()) + androidx.compose.animation.expandVertically(contentTween())
                        } else {
                            EnterTransition.None
                        },
                        exit = if (style.animateChanges) {
                            fadeOut(contentTween()) + androidx.compose.animation.shrinkVertically(contentTween())
                        } else {
                            ExitTransition.None
                        },
                    ) {
                        var textLayout by remember(displayedBody) {
                            androidx.compose.runtime.mutableStateOf<TextLayoutResult?>(null)
                        }
                        var textCoordinates by remember(displayedBody) {
                            androidx.compose.runtime.mutableStateOf<LayoutCoordinates?>(null)
                        }
                        Text(
                            text = displayedBody,
                            modifier = Modifier
                                .onGloballyPositioned { textCoordinates = it }
                                .detectAnnotatedLinkLongPress(
                                    text = displayedBody,
                                    layoutResult = { textLayout },
                                    coordinates = { textCoordinates },
                                    onLongPress = onLinkLongClick,
                                ),
                            color = colors.storyNormal,
                            fontFamily = typography.family,
                            fontSize = bodySize.sp,
                            style = animatedCommentTextStyle,
                            onTextLayout = { textLayout = it },
                        )
                    }

                    AnimatedVisibility(
                        visible = !textCollapsed && referenceLinks?.hasLinks() == true,
                        enter = if (style.animateChanges) {
                            fadeIn(contentTween()) + androidx.compose.animation.expandVertically(contentTween())
                        } else {
                            EnterTransition.None
                        },
                        exit = if (style.animateChanges) {
                            fadeOut(contentTween()) + androidx.compose.animation.shrinkVertically(contentTween())
                        } else {
                            ExitTransition.None
                        },
                    ) {
                        Column(modifier = Modifier.padding(top = 5.dp)) {
                            referenceLinks?.links.orEmpty().forEach { link ->
                                RuntimeReferenceRow(
                                    link = link,
                                    fontFamily = typography.family,
                                    textSize = referenceLabelSize,
                                    markerTextSize = typography.referenceMarkerSize,
                                    onLongClick = { bounds -> onReferenceLongClick(link, bounds) },
                                )
                            }
                        }
                    }
                }
            }
            if (style.showDivider) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (style.cardStyle) 8.dp else 4.dp)
                        .height(1.dp)
                        .background(colors.commentDivider),
                )
            }
        }
    }
}

@Composable
private fun RuntimeCommentMeta(
    author: String,
    age: String,
    byOp: Boolean,
    color: Color,
    emphasized: Boolean,
    hiddenPreview: String?,
    hiddenReplyCount: Int?,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    animateChanges: Boolean,
) {
    val colors = HarmonicTheme.colors
    val metaShape = if (emphasized) RoundedCornerShape(12.dp) else RectangleShape
    var displayedReplyCount by remember { mutableIntStateOf(hiddenReplyCount ?: 0) }
    LaunchedEffect(hiddenReplyCount) {
        if (hiddenReplyCount != null) {
            displayedReplyCount = hiddenReplyCount
        }
    }
    val replyCountIndicatorProgress by animateFloatAsState(
        targetValue = if (hiddenReplyCount != null) 1f else 0f,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "hidden reply count indicator",
    )
    val replyCountTextAlpha = if (hiddenReplyCount != null) {
        ((replyCountIndicatorProgress - 0.5f) * 2f).coerceIn(0f, 1f)
    } else {
        (replyCountIndicatorProgress * 2f).coerceIn(0f, 1f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(metaShape)
                .background(if (emphasized) colors.surfaceContainerHighest else Color.Transparent)
                .border(
                    1.dp,
                    if (emphasized) colors.commentDivider else Color.Transparent,
                    metaShape,
                )
                .padding(
                    horizontal = if (emphasized) 7.dp else 0.dp,
                    vertical = if (emphasized) 2.dp else 0.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = author,
                color = color,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                style = commentTextStyle,
            )
            if (byOp) {
                Text(
                    text = "OP",
                    modifier = Modifier
                        .padding(start = 3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color.copy(alpha = 0.14f))
                        .padding(horizontal = 3.dp),
                    color = color,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    style = compactCommentTextStyle,
                )
            }
            Text(
                text = age,
                modifier = Modifier.padding(start = 4.dp),
                color = color,
                fontFamily = fontFamily,
                fontSize = 13.sp,
                style = commentTextStyle,
            )
        }
        if (hiddenPreview != null) {
            Text(
                text = hiddenPreview,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                color = colors.storyDisabled,
                fontFamily = fontFamily,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = commentTextStyle,
            )
        } else {
            Box(Modifier.weight(1f))
        }
        if (hiddenReplyCount != null || replyCountIndicatorProgress > 0f) {
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accent)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                    .graphicsLayer {
                        val scale = 0.72f + 0.28f * replyCountIndicatorProgress
                        scaleX = scale
                        scaleY = scale
                        alpha = if (hiddenReplyCount != null) {
                            1f
                        } else {
                            replyCountIndicatorProgress
                        }
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                            pivotFractionX = 1f,
                            pivotFractionY = 0.5f,
                        )
                    },
            ) {
                Text(
                    text = "+$displayedReplyCount",
                    modifier = Modifier.graphicsLayer(alpha = replyCountTextAlpha),
                    color = Color.White,
                    fontFamily = fontFamily,
                    fontSize = 12.sp,
                    style = compactCommentTextStyle,
                )
            }
        }
    }
}

@Composable
private fun RuntimeReferenceRow(
    link: CollectedReferenceLinks.ReferenceLink,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    textSize: Float,
    markerTextSize: Float,
    onLongClick: (Rect) -> Unit,
) {
    val context = LocalContext.current
    val colors = HarmonicTheme.colors
    var bounds by remember(link.url) { androidx.compose.runtime.mutableStateOf(Rect.Zero) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .padding(top = 4.dp)
            .defaultMinSize(minHeight = 38.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, colors.commentDivider, RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = { Utils.openLinkMaybeHN(context, link.url) },
                onLongClick = { if (bounds.width > 0f && bounds.height > 0f) onLongClick(bounds) },
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = runCatching {
                FaviconLoader.getFaviconUrl(
                    link.url,
                    SettingsUtils.getPreferredFaviconProvider(context),
                )
            }.getOrNull(),
            fallback = painterResource(R.drawable.ic_public),
            error = painterResource(R.drawable.ic_public),
            contentDescription = null,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(17.dp),
        )
        if (link.hasNumber()) {
            Text(
                text = link.markerLabel.orEmpty(),
                modifier = Modifier.padding(end = 8.dp),
                color = colors.storyDisabled,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = markerTextSize.sp,
                style = compactCommentTextStyle,
            )
        }
        Text(
            text = ReferenceLinkRowUtils.getReferenceLinkLabel(link),
            modifier = Modifier.weight(1f),
            color = colors.storyNormal,
            fontFamily = fontFamily,
            fontSize = textSize.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = compactAnimatedCommentTextStyle,
        )
    }
}

private fun htmlAnnotatedString(
    html: String,
    linkStyles: TextLinkStyles,
    linkListener: LinkInteractionListener,
): AnnotatedString = runCatching {
    AnnotatedString.fromHtml(
        htmlString = preserveLegacyCommentParagraphSpacing(html),
        linkStyles = linkStyles,
        linkInteractionListener = linkListener,
    )
}.getOrElse {
    AnnotatedString(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString())
}

private fun preserveLegacyCommentParagraphSpacing(html: String): String = html
    .replace(Regex("<p\\s*>", RegexOption.IGNORE_CASE), "<br><br>")
    .replace(Regex("</p>\\s*<p", RegexOption.IGNORE_CASE), "</p><br><p")
    .replace(Regex("</div>\\s*<div", RegexOption.IGNORE_CASE), "</div><br><div")

/**
 * Adds legacy-equivalent long-press handling to links rendered by Compose text without taking
 * over the normal tap gesture used by [LinkAnnotation].
 */
internal fun Modifier.detectAnnotatedLinkLongPress(
    text: AnnotatedString,
    layoutResult: () -> TextLayoutResult?,
    coordinates: () -> LayoutCoordinates?,
    onLongPress: (url: String, label: String, bounds: Rect) -> Unit,
): Modifier = pointerInput(text, onLongPress) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
        val layout = layoutResult() ?: return@awaitEachGesture
        val position = longPress.position
        val offset = layout.getOffsetForPosition(position)
            .coerceIn(0, text.length.coerceAtLeast(1) - 1)
        val range = text.getLinkAnnotations(offset, (offset + 1).coerceAtMost(text.length))
            .firstOrNull { it.item is LinkAnnotation.Url }
            ?: return@awaitEachGesture
        val link = range.item as LinkAnnotation.Url
        val windowBounds = annotatedRangeBoundsInWindow(
            range.start,
            range.end,
            text.length,
            layout,
            coordinates(),
        ) ?: return@awaitEachGesture
        val label = text.text.substring(range.start, range.end)
            .trim()
            .ifBlank { link.url }
        longPress.consume()
        onLongPress(link.url, label, windowBounds)
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

private fun annotatedRangeBoundsInWindow(
    start: Int,
    end: Int,
    textLength: Int,
    layout: TextLayoutResult,
    coordinates: LayoutCoordinates?,
): Rect? {
    if (coordinates == null || !coordinates.isAttached || textLength <= 0) return null
    val first = start.coerceIn(0, textLength - 1)
    val lastExclusive = end.coerceIn(first + 1, textLength)
    var localBounds = layout.getBoundingBox(first)
    for (offset in (first + 1) until lastExclusive) {
        localBounds = localBounds.expandToInclude(layout.getBoundingBox(offset))
    }
    val topLeft = coordinates.localToWindow(Offset(localBounds.left, localBounds.top))
    val bottomRight = coordinates.localToWindow(Offset(localBounds.right, localBounds.bottom))
    return Rect(topLeft, bottomRight)
}

private fun Rect.expandToInclude(other: Rect): Rect = Rect(
    left = minOf(left, other.left),
    top = minOf(top, other.top),
    right = maxOf(right, other.right),
    bottom = maxOf(bottom, other.bottom),
)

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
    val cornerRadius by animateDpAsState(
        targetValue = if (style.emphasizeMeta) 12.dp else 0.dp,
        animationSpec = contentTween(),
        label = "comment meta corners",
    )
    val shape = RoundedCornerShape(cornerRadius)

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
