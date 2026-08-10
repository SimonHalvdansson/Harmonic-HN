package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_public
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.CollectedReferenceLinks
import com.simon.harmonichackernews.utils.ReferenceLinkRowUtils
import kotlin.math.min
import org.jetbrains.compose.resources.painterResource

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

/** Settings preview rendered by the same shared row primitives as runtime comments. */
@Composable
fun CommentItem(
    model: CommentItemUiModel,
    style: CommentItemStyle,
    modifier: Modifier = Modifier,
) {
    val typography = rememberContentTypography(
        preferredFont = style.preferredFont,
        commentTextSize = style.textSize,
    )
    SharedCommentSurface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
        style = style,
        depth = 0,
        showIndicator = style.depthIndicatorMode != "none",
        indicatorColor = depthColors().first(),
        highlighted = false,
        onClick = {},
        onLongClick = {},
    ) {
        CommentMeta(
            author = model.author,
            age = model.age,
            byOp = false,
            byUser = false,
            userTag = null,
            hiddenPreview = null,
            hiddenReplyCount = null,
            emphasized = style.emphasizeMeta,
            fontFamily = typography.family,
        )
        Text(
            text = model.body,
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = typography.family,
            fontSize = typography.commentTextSize.sp,
            style = animatedCommentTextStyle,
        )
        if (style.collectLinks) {
            ReferenceRow(
                marker = model.referenceMarker,
                label = model.referenceUrl,
                onClick = {},
                onLongClick = {},
            )
        }
    }
}

/**
 * Complete platform-neutral runtime comment row. Android now supplies only link-opening effects;
 * hierarchy, collapse presentation, rich HTML, references, search highlighting and gestures live
 * in shared UI.
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
    onLinkClick: (String) -> Unit = {},
) {
    val colors = HarmonicTheme.colors
    val typography = rememberContentTypography(
        preferredFont = style.preferredFont,
        commentTextSize = style.textSize,
    )
    val effectiveDepth = if (flattenHierarchy) 0 else comment.depth
    val showIndicator = !flattenHierarchy && style.depthIndicatorMode != "none" &&
        (effectiveDepth > 0 || showTopLevelIndicator)
    val indicatorIndex = (effectiveDepth + if (showTopLevelIndicator) 0 else -1)
        .coerceAtLeast(0) % depthColors().size
    val references = remember(comment.expandedAnchorText, style.collectLinks) {
        if (style.collectLinks) CollectedReferenceLinks.parse(comment.expandedAnchorText) else null
    }
    val bodyHtml = references?.takeIf(CollectedReferenceLinks.Result::hasLinks)?.bodyHtml
        ?: comment.expandedAnchorText.orEmpty()
    val body = remember(bodyHtml, colors.link) {
        htmlAnnotatedString(bodyHtml, colors.link)
    }
    val markedColor = if (colors.background.luminance() < 0.5f) Color(0xfffce205) else Color(0xffcc7722)
    val displayedBody = remember(body, searchTerm, markedColor) {
        highlightSearchMatches(body, searchTerm, markedColor)
    }
    val hiddenPreview = remember(comment.text) {
        Ksoup.parse(comment.text.orEmpty().take(240)).text().replace('\n', ' ').take(120)
    }
    val textCollapsed = !forceExpanded && !comment.expanded && collapseParent
    var textLayout by remember(comment.id, body) { mutableStateOf<TextLayoutResult?>(null) }
    var textCoordinates by remember(comment.id) { mutableStateOf<LayoutCoordinates?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val start = min(16.dp.value + 12.dp.value * effectiveDepth, maxWidth.value * 0.6f).dp
        SharedCommentSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = start, end = 16.dp)
                .padding(vertical = if (style.cardStyle) 2.dp else 6.dp),
            style = style,
            depth = effectiveDepth,
            showIndicator = showIndicator,
            indicatorColor = depthColors()[indicatorIndex],
            highlighted = highlighted,
            onClick = onToggleExpanded,
            onLongClick = onShowActions,
        ) {
            CommentMeta(
                author = comment.by.orEmpty(),
                age = comment.timeFormatted,
                byOp = comment.by == storyAuthor,
                byUser = !accountUser.isNullOrBlank() && comment.by == accountUser,
                userTag = userTag,
                hiddenPreview = hiddenPreview.takeIf { textCollapsed },
                hiddenReplyCount = hiddenReplyCount.takeIf { textCollapsed },
                emphasized = style.emphasizeMeta,
                fontFamily = typography.family,
            )
            AnimatedVisibility(!textCollapsed) {
                Column {
                    Text(
                        text = displayedBody,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { textCoordinates = it }
                            .detectAnnotatedLinkGestures(
                                text = displayedBody,
                                layoutResult = { textLayout },
                                coordinates = { textCoordinates },
                                onClick = onLinkClick,
                                onLongPress = onLinkLongClick,
                            ),
                        onTextLayout = { textLayout = it },
                        color = colors.storyNormal,
                        fontFamily = typography.family,
                        fontSize = typography.commentTextSize.sp,
                        style = animatedCommentTextStyle,
                    )
                    references?.links?.forEach { link ->
                        var bounds by remember(link.url) { mutableStateOf(Rect.Zero) }
                        ReferenceRow(
                            marker = link.markerLabel.orEmpty(),
                            label = ReferenceLinkRowUtils.getReferenceLinkLabel(link),
                            modifier = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() },
                            onClick = { link.url?.let(onLinkClick) },
                            onLongClick = {
                                if (bounds.width > 0f && bounds.height > 0f) {
                                    onReferenceLongClick(link, bounds)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedCommentSurface(
    modifier: Modifier,
    style: CommentItemStyle,
    depth: Int,
    showIndicator: Boolean,
    indicatorColor: Color,
    highlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = HarmonicTheme.colors
    val shape = if (style.cardStyle) RoundedCornerShape(8.dp) else RectangleShape
    val baseBackground = if (style.cardStyle) colors.surfaceContainerHigh else colors.background
    val overlayAlpha = if (highlighted) {
        if (baseBackground.luminance() < 0.5f) 0.14f else 0.08f
    } else 0f
    val background = colors.storyNormal.copy(alpha = overlayAlpha).compositeOver(baseBackground)
    val shadowPadding by animateDpAsState(
        if (style.cardStyle) 4.dp else 0.dp,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment card padding",
    )
    val indicatorAlpha by animateFloatAsState(
        if (showIndicator) 1f else 0f,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment indicator",
    )
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(shadowPadding)
                .height(IntrinsicSize.Min)
                .shadow(if (style.cardStyle) 1.dp else 0.dp, shape, clip = false)
                .clip(shape)
                .background(background)
                .border(
                    if (style.cardStyle && style.showCardBorder) 1.dp else 0.dp,
                    colors.commentDivider,
                    shape,
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            Box(
                Modifier
                    .width(if (style.cardStyle) 3.5.dp else 2.5.dp)
                    .fillMaxHeight()
                    .graphicsLayer(alpha = indicatorAlpha)
                    .background(indicatorColor),
            )
            Column(
                Modifier.weight(1f).padding(
                    horizontal = if (style.cardStyle) 8.dp else 5.dp,
                    vertical = if (style.cardStyle) 7.dp else 5.dp,
                ),
            ) { content() }
        }
        AnimatedVisibility(style.showDivider) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(1.dp)
                    .background(colors.commentDivider),
            )
        }
    }
}

@Composable
private fun CommentMeta(
    author: String,
    age: String,
    byOp: Boolean,
    byUser: Boolean,
    userTag: String?,
    hiddenPreview: String?,
    hiddenReplyCount: Int?,
    emphasized: Boolean,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
) {
    val colors = HarmonicTheme.colors
    val metaColor = when {
        byUser -> colors.accent
        byOp -> colors.link
        emphasized -> colors.storyNormal
        else -> colors.storyDisabled
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(if (emphasized) 12.dp else 0.dp))
                .background(if (emphasized) colors.surfaceContainerHighest else Color.Transparent)
                .padding(horizontal = if (emphasized) 7.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(author, color = metaColor, fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (byOp) {
                Text(
                    "OP",
                    modifier = Modifier.padding(start = 3.dp).clip(RoundedCornerShape(3.dp))
                        .background(metaColor.copy(alpha = 0.14f)).padding(horizontal = 3.dp),
                    color = metaColor,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                )
            }
            Text(age, modifier = Modifier.padding(start = 4.dp), color = metaColor, fontFamily = fontFamily, fontSize = 13.sp)
            if (!userTag.isNullOrBlank()) {
                Text(" • $userTag", color = metaColor, fontFamily = fontFamily, fontSize = 12.sp)
            }
        }
        if (hiddenPreview != null) {
            Text(
                hiddenPreview,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
                color = colors.storyDisabled,
                fontFamily = fontFamily,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Box(Modifier.weight(1f))
        }
        hiddenReplyCount?.let {
            Text(
                "+$it",
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.accent)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                color = Color.White,
                fontFamily = fontFamily,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ReferenceRow(
    marker: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = HarmonicTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .defaultMinSize(minHeight = 38.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, colors.commentDivider, RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_public),
            contentDescription = null,
            tint = colors.drawable,
            modifier = Modifier.padding(end = 8.dp).size(17.dp),
        )
        if (marker.isNotBlank()) {
            Text(marker, modifier = Modifier.padding(end = 8.dp), color = colors.storyDisabled, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = colors.storyNormal,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun htmlAnnotatedString(
    html: String,
    linkColor: Color,
): AnnotatedString = runCatching {
    val document = Ksoup.parse(preserveLegacyCommentParagraphSpacing(html))
    val plainText = document.body().wholeText().trim()
    buildAnnotatedString {
        append(plainText)
        var cursor = 0
        document.select("a[href]").forEach { anchor ->
            val label = anchor.text().trim()
            val url = anchor.attr("href").trim()
            if (label.isNotEmpty() && url.isNotEmpty()) {
                val start = plainText.indexOf(label, cursor)
                if (start >= 0) {
                    val end = start + label.length
                    addStyle(
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                        start,
                        end,
                    )
                    addStringAnnotation(URL_ANNOTATION, url, start, end)
                    cursor = end
                }
            }
        }
    }
}.getOrElse { AnnotatedString(Ksoup.parse(html).text()) }

private fun preserveLegacyCommentParagraphSpacing(html: String): String = html
    .replace(Regex("<p\\s*>", RegexOption.IGNORE_CASE), "<br><br>")
    .replace(Regex("</p>\\s*<p", RegexOption.IGNORE_CASE), "</p><br><p")
    .replace(Regex("</div>\\s*<div", RegexOption.IGNORE_CASE), "</div><br><div")

private fun highlightSearchMatches(
    body: AnnotatedString,
    searchTerm: String,
    markedColor: Color,
): AnnotatedString {
    val needle = searchTerm.trim()
    if (needle.isEmpty()) return body
    return buildAnnotatedString {
        append(body)
        val haystack = body.text.lowercase()
        val normalizedNeedle = needle.lowercase()
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

private fun Modifier.detectAnnotatedLinkGestures(
    text: AnnotatedString,
    layoutResult: () -> TextLayoutResult?,
    coordinates: () -> LayoutCoordinates?,
    onClick: (url: String) -> Unit,
    onLongPress: (url: String, label: String, bounds: Rect) -> Unit,
): Modifier = pointerInput(text, onClick, onLongPress) {
    detectTapGestures(
        onTap = { position ->
            val layout = layoutResult() ?: return@detectTapGestures
            if (text.isEmpty()) return@detectTapGestures
            val offset = layout.getOffsetForPosition(position).coerceIn(0, text.length - 1)
            text.getStringAnnotations(
                URL_ANNOTATION,
                offset,
                (offset + 1).coerceAtMost(text.length),
            ).firstOrNull()?.let { onClick(it.item) }
        },
        onLongPress = { position ->
            val layout = layoutResult() ?: return@detectTapGestures
            if (text.isEmpty()) return@detectTapGestures
            val offset = layout.getOffsetForPosition(position).coerceIn(0, text.length - 1)
            val range = text.getStringAnnotations(
                URL_ANNOTATION,
                offset,
                (offset + 1).coerceAtMost(text.length),
            ).firstOrNull() ?: return@detectTapGestures
            val bounds = annotatedRangeBoundsInWindow(
                range.start,
                range.end,
                text.length,
                layout,
                coordinates(),
            ) ?: return@detectTapGestures
            onLongPress(
                range.item,
                text.text.substring(range.start, range.end).trim().ifBlank { range.item },
                bounds,
            )
        },
    )
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
        val other = layout.getBoundingBox(offset)
        localBounds = Rect(
            minOf(localBounds.left, other.left),
            minOf(localBounds.top, other.top),
            maxOf(localBounds.right, other.right),
            maxOf(localBounds.bottom, other.bottom),
        )
    }
    val topLeft = coordinates.localToWindow(Offset(localBounds.left, localBounds.top))
    val bottomRight = coordinates.localToWindow(Offset(localBounds.right, localBounds.bottom))
    return Rect(topLeft, bottomRight)
}

private fun depthColors(): List<Color> = listOf(
    Color(0xff5e97f6),
    Color(0xff9ccc65),
    Color(0xffffb74d),
    Color(0xffba68c8),
    Color(0xff4dd0e1),
    Color(0xffef5350),
    Color(0xffffd54f),
)

private val animatedCommentTextStyle = TextStyle(
    textMotion = TextMotion.Animated,
)

private const val URL_ANNOTATION = "url"
