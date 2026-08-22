package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_public
import com.simon.harmonichackernews.ui.comments.CommentActionSourceGeometry
import com.simon.harmonichackernews.ui.comments.captureCommentActionSourceContent
import com.simon.harmonichackernews.ui.common.captureSharedTransformSourceContent
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.CollectedReferenceLinks
import com.simon.harmonichackernews.utils.ReferenceLinkRowUtils
import kotlin.math.min
import kotlin.math.roundToInt
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
    val transparentNonCardBackground: Boolean = false,
)

private class CommentItemGeometry {
    var coordinates: LayoutCoordinates? = null
    var contentLayer: GraphicsLayer? = null
    var containerColor: Color = Color.Transparent
    var containerCornerRadiusDp: Float = 0f
    var containerElevationDp: Float = 0f
    var containerBorderColor: Color = Color.Transparent
    var containerBorderWidthDp: Float = 0f

    fun boundsInWindowOrNull(): Rect? = coordinates
        ?.takeIf { it.isAttached }
        ?.let {
            Rect(
                offset = it.positionInWindow(),
                size = Size(it.size.width.toFloat(), it.size.height.toFloat()),
            )
        }
        ?.takeIf { it.width > 0f && it.height > 0f }

    fun snapshot(): CommentActionSourceGeometry? {
        val container = boundsInWindowOrNull() ?: return null
        return CommentActionSourceGeometry(
            container = container,
            containerColor = containerColor,
            containerCornerRadiusDp = containerCornerRadiusDp,
            containerElevationDp = containerElevationDp,
            containerBorderColor = containerBorderColor,
            containerBorderWidthDp = containerBorderWidthDp,
            contentLayer = contentLayer?.takeUnless(GraphicsLayer::isReleased),
        )
    }
}

private enum class CommentActionSourceGesture {
    Click,
    LongClick,
}

val SettingsCommentPreviewModel = CommentItemUiModel(
    author = "pg",
    age = "1h",
    body = "This reminds me of the old systems where the boring path was often the most durable one. " +
        "The less hidden state there is, the easier it is to reason about." +
        "<p>[0] <a href=\"https://example.com/reference\">https://example.com/reference</a></p>",
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
    val previewReferences = remember(model.body, style.collectLinks) {
        if (style.collectLinks) CollectedReferenceLinks.parse(model.body) else null
    }
    val previewBody = previewReferences
        ?.takeIf(CollectedReferenceLinks.Result::hasLinks)
        ?.bodyHtml
        ?: model.body
    val bodySize by animateFloatAsState(
        targetValue = typography.commentTextSize,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment preview text size",
    )
    SharedCommentSurface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
        style = style,
        showIndicator = style.depthIndicatorMode != "none",
        indicatorColor = CommentDepthColors.first(),
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
            showHiddenReplyCount = false,
            emphasized = style.emphasizeMeta,
            fontFamily = typography.family,
            animateChanges = style.animateChanges,
        )
        CommentBodyText(
            html = previewBody,
            searchTerm = "",
            markedColor = HarmonicTheme.colors.storyNormal,
            fontFamily = typography.family,
            fontSize = bodySize,
            onLinkClick = {},
            onLinkLongClick = { _, _, _ -> },
        )
        AnimatedVisibility(
            visible = style.collectLinks,
            enter = fadeIn(contentTween()) + expandVertically(contentTween()),
            exit = fadeOut(contentTween()) + shrinkVertically(contentTween()),
        ) {
            ReferenceRow(
                marker = model.referenceMarker,
                label = model.referenceUrl,
                modifier = Modifier.padding(top = 5.dp),
                onClick = {},
                onLongClick = { _, _ -> },
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
    comment: PortableCommentItem,
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
    suppressedReferenceUrl: String? = null,
    captureActionSource: Boolean = false,
    showActionsOnClick: Boolean = false,
    onToggleExpanded: (Rect?) -> Unit,
    onShowActions: (Rect?) -> Unit,
    onActionSourceGeometryChanged: ((CommentActionSourceGeometry) -> Unit)? = null,
    onLinkLongClick: (String, String, Rect) -> Unit,
    onReferenceLongClick: (CollectedReferenceLinks.ReferenceLink, Rect, GraphicsLayer?) -> Unit,
    onLinkClick: (String) -> Unit = {},
) {
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
    val effectiveDepth = if (flattenHierarchy) 0 else comment.depth
    val showIndicator = !flattenHierarchy && style.depthIndicatorMode != "none" &&
        (effectiveDepth > 0 || showTopLevelIndicator)
    val indicatorIndex = (effectiveDepth + if (showTopLevelIndicator) 0 else -1)
        .coerceAtLeast(0) % CommentDepthColors.size
    val references = remember(comment.expandedAnchorText, style.collectLinks) {
        if (style.collectLinks) CollectedReferenceLinks.parse(comment.expandedAnchorText) else null
    }
    val contentBlocks = remember(references, comment.expandedAnchorText) {
        references
            ?.takeIf(CollectedReferenceLinks.Result::hasLinks)
            ?.contentBlocks
            ?: listOf(CollectedReferenceLinks.ContentBlock.text(comment.expandedAnchorText))
    }
    val hasInterleavedReferences = references?.hasInterleavedLinks() == true
    val firstReferenceIndex = contentBlocks.indexOfFirst { it.getLink() != null }
    val markedColor = if (colors.background.luminance() < 0.5f) Color(0xfffce205) else Color(0xffcc7722)
    val textCollapsed = !forceExpanded && !comment.expanded && collapseParent
    val hiddenPreview = remember(comment.text, textCollapsed) {
        if (textCollapsed) {
            Ksoup.parse(comment.text.orEmpty().take(240)).text().replace('\n', ' ').take(120)
        } else {
            null
        }
    }

    val top = if (style.cardStyle) {
        if (effectiveDepth > 0 && !collapseParent) 2.dp else 0.dp
    } else if (effectiveDepth > 0 && !collapseParent) {
        10.dp
    } else {
        6.dp
    }
    val bottom = if (style.cardStyle) 0.dp else 6.dp
    val itemGeometry = remember { CommentItemGeometry() }
    var pendingActionSourceGesture by remember {
        mutableStateOf<CommentActionSourceGesture?>(null)
    }
    fun publishActionSourceGeometry() {
        itemGeometry.snapshot()?.let { onActionSourceGeometryChanged?.invoke(it) }
    }
    LaunchedEffect(pendingActionSourceGesture) {
        val gesture = pendingActionSourceGesture ?: return@LaunchedEffect
        // Record the row once only when the gesture is going to open the shared-element overlay.
        withFrameNanos { }
        publishActionSourceGeometry()
        val bounds = itemGeometry.boundsInWindowOrNull()
        when (gesture) {
            CommentActionSourceGesture.Click -> onToggleExpanded(bounds)
            CommentActionSourceGesture.LongClick -> onShowActions(bounds)
        }
        pendingActionSourceGesture = null
    }
    CommentItemLayout(
        modifier = modifier,
        effectiveDepth = effectiveDepth,
        cardStyle = style.cardStyle,
        topPadding = top,
        bottomPadding = bottom,
    ) {
        SharedCommentSurface(
            modifier = Modifier.fillMaxWidth(),
            style = style,
            showIndicator = showIndicator,
            indicatorColor = CommentDepthColors[indicatorIndex],
            highlighted = highlighted,
            itemGeometry = itemGeometry,
            captureSource = captureActionSource || pendingActionSourceGesture != null,
            onClick = {
                if (showActionsOnClick) {
                    pendingActionSourceGesture = CommentActionSourceGesture.Click
                } else {
                    onToggleExpanded(itemGeometry.boundsInWindowOrNull())
                }
            },
            onLongClick = {
                if (showActionsOnClick) {
                    onShowActions(itemGeometry.boundsInWindowOrNull())
                } else {
                    pendingActionSourceGesture = CommentActionSourceGesture.LongClick
                }
            },
        ) {
            CommentMeta(
                author = comment.by.orEmpty(),
                age = comment.timeFormatted,
                byOp = comment.by == storyAuthor,
                byUser = !accountUser.isNullOrBlank() && comment.by == accountUser,
                userTag = userTag,
                hiddenPreview = hiddenPreview,
                hiddenReplyCount = hiddenReplyCount.takeIf {
                    it > 0
                },
                showHiddenReplyCount = hiddenReplyCount > 0 &&
                    !forceExpanded && !comment.expanded,
                emphasized = style.emphasizeMeta,
                fontFamily = typography.family,
                animateChanges = style.animateChanges,
            )
            AnimatedVisibility(
                visible = !textCollapsed,
                enter = fadeIn(contentTween()) + expandVertically(contentTween()),
                exit = fadeOut(contentTween()) + shrinkVertically(contentTween()),
            ) {
                Column {
                    contentBlocks.forEachIndexed { index, block ->
                        val link = block.getLink()
                        if (link == null) {
                            CommentBodyText(
                                html = block.bodyHtml.orEmpty(),
                                modifier = if (hasInterleavedReferences && index > 0) {
                                    Modifier.padding(top = 5.dp)
                                } else {
                                    Modifier
                                },
                                searchTerm = searchTerm,
                                markedColor = markedColor,
                                fontFamily = typography.family,
                                fontSize = bodySize,
                                onLinkClick = onLinkClick,
                                onLinkLongClick = onLinkLongClick,
                            )
                        } else {
                            ReferenceRow(
                                marker = link.markerLabel.orEmpty(),
                                label = ReferenceLinkRowUtils.getReferenceLinkLabel(link),
                                modifier = when {
                                    hasInterleavedReferences -> Modifier.padding(bottom = 2.dp)
                                    index == firstReferenceIndex -> Modifier.padding(top = 5.dp)
                                    else -> Modifier
                                },
                                suppressed = link.url == suppressedReferenceUrl,
                                onClick = { link.url?.let(onLinkClick) },
                                onLongClick = { bounds, sourceContentLayer ->
                                    if (bounds.width > 0f && bounds.height > 0f) {
                                        onReferenceLongClick(link, bounds, sourceContentLayer)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Applies depth indentation from incoming constraints without a BoxWithConstraints subcompose. */
@Composable
private fun CommentItemLayout(
    modifier: Modifier,
    effectiveDepth: Int,
    cardStyle: Boolean,
    topPadding: Dp,
    bottomPadding: Dp,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = content,
    ) { measurables, constraints ->
        val desiredStart = 16.dp.roundToPx() + 12.dp.roundToPx() * effectiveDepth
        val cappedStart = min(desiredStart, (constraints.maxWidth * 0.6f).roundToInt())
        val shadowPadding = if (cardStyle) 4.dp.roundToPx() else 0
        val startPadding = (cappedStart - shadowPadding).coerceAtLeast(0)
        val endPadding = 16.dp.roundToPx()
        val topPaddingPx = topPadding.roundToPx()
        val bottomPaddingPx = bottomPadding.roundToPx()
        val contentWidth = (constraints.maxWidth - startPadding - endPadding).coerceAtLeast(0)
        val contentHeight = if (constraints.hasBoundedHeight) {
            (constraints.maxHeight - topPaddingPx - bottomPaddingPx).coerceAtLeast(0)
        } else {
            constraints.maxHeight
        }
        val placeable = measurables.single().measure(
            constraints.copy(
                minWidth = contentWidth,
                maxWidth = contentWidth,
                minHeight = 0,
                maxHeight = contentHeight,
            ),
        )

        layout(
            width = constraints.maxWidth,
            height = topPaddingPx + placeable.height + bottomPaddingPx,
        ) {
            placeable.placeRelative(startPadding, topPaddingPx)
        }
    }
}

@Composable
private fun CommentBodyText(
    html: String,
    modifier: Modifier = Modifier,
    searchTerm: String,
    markedColor: Color,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    fontSize: Float,
    onLinkClick: (String) -> Unit,
    onLinkLongClick: (String, String, Rect) -> Unit,
) {
    val colors = HarmonicTheme.colors
    val linkGestureState = remember(html) { AnnotatedLinkGestureState() }
    val linkListener = remember(linkGestureState, onLinkClick) {
        LinkInteractionListener { annotation ->
            if (annotation is LinkAnnotation.Url &&
                !linkGestureState.consumeSuppressedLinkClick()
            ) {
                onLinkClick(annotation.url)
            }
        }
    }
    val body = remember(html, colors.link, linkListener) {
        htmlAnnotatedString(html, colors.link, linkListener)
    }
    val displayedBody = remember(body, searchTerm, markedColor) {
        highlightSearchMatches(body, searchTerm, markedColor)
    }
    var textLayout by remember(body) { mutableStateOf<TextLayoutResult?>(null) }
    var textCoordinates by remember(body) { mutableStateOf<LayoutCoordinates?>(null) }

    if (displayedBody.isNotEmpty()) {
        Text(
            text = displayedBody,
            modifier = modifier
                .fillMaxWidth()
                .onGloballyPositioned { textCoordinates = it }
                .detectAnnotatedLinkLongPress(
                    text = displayedBody,
                    layoutResult = { textLayout },
                    coordinates = { textCoordinates },
                    linkGestureState = linkGestureState,
                    onLongPress = onLinkLongClick,
                ),
            onTextLayout = { textLayout = it },
            color = colors.storyNormal,
            fontFamily = fontFamily,
            fontSize = fontSize.sp,
            style = animatedCommentTextStyle,
        )
    }
}

@Composable
private fun SharedCommentSurface(
    modifier: Modifier,
    style: CommentItemStyle,
    showIndicator: Boolean,
    indicatorColor: Color,
    highlighted: Boolean,
    itemGeometry: CommentItemGeometry? = null,
    captureSource: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = HarmonicTheme.colors
    val shapeRadius by animateDpAsState(
        if (style.cardStyle) 8.dp else 0.dp,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment corner radius",
    )
    val shape = RoundedCornerShape(shapeRadius)
    val baseBackground = when {
        style.cardStyle -> colors.surfaceContainerHigh
        style.transparentNonCardBackground -> Color.Transparent
        else -> colors.background
    }
    val overlayAlpha = if (highlighted) {
        if (baseBackground.luminance() < 0.5f) 0.14f else 0.08f
    } else 0f
    val targetBackground = colors.storyNormal.copy(alpha = overlayAlpha).compositeOver(baseBackground)
    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment background",
    )
    val shadowPadding by animateDpAsState(
        if (style.cardStyle) 4.dp else 0.dp,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment card padding",
    )
    val cardProgress by animateFloatAsState(
        if (style.cardStyle) 1f else 0f,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment card progress",
    )
    val borderAlpha by animateFloatAsState(
        if (style.cardStyle && style.showCardBorder) 1f else 0f,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment border",
    )
    val indicatorAlpha by animateFloatAsState(
        if (showIndicator) 1f else 0f,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment indicator",
    )
    val indicatorWidth by animateDpAsState(
        if (showIndicator || style.cardStyle) {
            if (style.cardStyle) 3.5.dp else 2.5.dp
        } else 0.dp,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment indicator width",
    )
    val indicatorMargin by animateDpAsState(
        if (showIndicator || style.cardStyle) {
            if (style.cardStyle) 4.dp else 8.dp
        } else 0.dp,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment indicator margin",
    )
    val contentStartPadding by animateDpAsState(
        if (style.cardStyle) 4.5.dp else 5.dp,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment content start padding",
    )
    val contentEndPadding by animateDpAsState(
        if (style.cardStyle) 8.dp else 4.dp,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment content end padding",
    )
    val contentVerticalPadding by animateDpAsState(
        if (style.cardStyle) 7.dp else 5.dp,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment content vertical padding",
    )
    val dividerInset by animateDpAsState(
        if (style.cardStyle) 8.dp else 4.dp,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment divider inset",
    )
    val dividerHeight by animateDpAsState(
        if (style.showDivider) 4.dp else 0.dp,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment divider height",
    )
    val dividerAlpha by animateFloatAsState(
        if (style.showDivider) 1f else 0f,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment divider alpha",
    )
    SideEffect {
        itemGeometry?.containerColor = background
        itemGeometry?.containerCornerRadiusDp = if (style.cardStyle) 8f else 0f
        itemGeometry?.containerElevationDp = if (style.cardStyle) 1f else 0f
        itemGeometry?.containerBorderColor = colors.commentDivider
        itemGeometry?.containerBorderWidthDp =
            if (style.cardStyle && style.showCardBorder) 1f else 0f
    }
    val contentCaptureModifier = if (itemGeometry != null && captureSource) {
        Modifier.captureCommentActionSourceContent { itemGeometry.contentLayer = it }
    } else {
        Modifier
    }
    val sourceCaptureModifier = if (itemGeometry == null) {
        Modifier
    } else {
        Modifier
            .onGloballyPositioned { itemGeometry.coordinates = it }
            .then(contentCaptureModifier)
    }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().padding(shadowPadding)) {
            Layout(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow((cardProgress * 1f).dp, shape, clip = false)
                    .clip(shape)
                    .background(background)
                    .border(
                        1.dp,
                        colors.commentDivider.copy(alpha = borderAlpha),
                        shape,
                    )
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = colors.storyDisabled.copy(alpha = 0.35f)),
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                    .then(sourceCaptureModifier),
                content = {
                    Box(
                        Modifier
                            .width(indicatorWidth)
                            .graphicsLayer(alpha = indicatorAlpha)
                            .background(indicatorColor),
                    )
                    Column(
                        Modifier.padding(
                            start = contentStartPadding,
                            top = contentVerticalPadding,
                            end = contentEndPadding,
                            bottom = contentVerticalPadding,
                        ),
                    ) { content() }
                },
            ) { measurables, constraints ->
                val indicatorWidthPx = indicatorWidth.roundToPx()
                    .coerceAtMost(constraints.maxWidth)
                val indicatorMarginPx = indicatorMargin.roundToPx()
                    .coerceAtMost((constraints.maxWidth - indicatorWidthPx).coerceAtLeast(0))
                val contentWidth = (
                    constraints.maxWidth - indicatorWidthPx - indicatorMarginPx
                ).coerceAtLeast(0)
                val contentPlaceable = measurables[1].measure(
                    constraints.copy(
                        minWidth = contentWidth,
                        maxWidth = contentWidth,
                        minHeight = 0,
                    ),
                )
                val rowHeight = contentPlaceable.height
                val indicatorPlaceable = measurables[0].measure(
                    constraints.copy(
                        minWidth = indicatorWidthPx,
                        maxWidth = indicatorWidthPx,
                        minHeight = rowHeight,
                        maxHeight = rowHeight,
                    ),
                )

                layout(constraints.maxWidth, rowHeight) {
                    indicatorPlaceable.placeRelative(0, 0)
                    contentPlaceable.placeRelative(
                        indicatorWidthPx + indicatorMarginPx,
                        0,
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(dividerHeight).padding(horizontal = dividerInset),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxWidth().height(1.dp).graphicsLayer(alpha = dividerAlpha)
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
    showHiddenReplyCount: Boolean,
    emphasized: Boolean,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    animateChanges: Boolean,
) {
    val colors = HarmonicTheme.colors
    val metaColor = when {
        byUser -> colors.accent
        byOp -> colors.link
        emphasized -> colors.storyNormal
        else -> colors.storyDisabled
    }
    val metaRadius by animateDpAsState(
        if (emphasized) 12.dp else 0.dp,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "comment meta radius",
    )
    val metaHorizontalPadding by animateDpAsState(
        if (emphasized) 7.dp else 0.dp,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "comment meta horizontal padding",
    )
    val metaVerticalPadding by animateDpAsState(
        if (emphasized) 2.dp else 0.dp,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "comment meta vertical padding",
    )
    val metaBackground by animateColorAsState(
        colors.surfaceContainerHighest.copy(alpha = if (emphasized) 1f else 0f),
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "comment meta background",
    )
    val metaBorderAlpha by animateFloatAsState(
        if (emphasized) 1f else 0f,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "comment meta border",
    )
    val hiddenReplyCountAlpha by animateFloatAsState(
        if (showHiddenReplyCount) 1f else 0f,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "hidden reply count",
    )
    val metaShape = RoundedCornerShape(metaRadius)
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(metaShape)
                .background(metaBackground)
                .border(1.dp, colors.commentDivider.copy(alpha = metaBorderAlpha), metaShape)
                .padding(horizontal = metaHorizontalPadding, vertical = metaVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(author, color = metaColor, fontFamily = fontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (byOp) {
                Text(
                    "OP",
                    modifier = Modifier.padding(start = 3.dp).height(14.dp).clip(RoundedCornerShape(3.dp))
                        .background(metaColor.copy(alpha = 0.14f)).padding(horizontal = 3.dp),
                    color = metaColor,
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    style = compactCommentTextStyle,
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
                modifier = Modifier
                    .graphicsLayer(alpha = hiddenReplyCountAlpha)
                    .clip(RoundedCornerShape(7.dp))
                    .background(colors.accent)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                    .then(
                        if (showHiddenReplyCount) Modifier else Modifier.clearAndSetSemantics { },
                    ),
                color = Color.White,
                fontFamily = fontFamily,
                fontSize = 12.sp,
                style = compactCommentTextStyle,
            )
        }
    }
}

@Composable
private fun ReferenceRow(
    marker: String,
    label: String,
    modifier: Modifier = Modifier,
    suppressed: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (Rect, GraphicsLayer?) -> Unit,
) {
    val colors = HarmonicTheme.colors
    var bounds by remember(label) { mutableStateOf(Rect.Zero) }
    var sourceContentLayer by remember(label) { mutableStateOf<GraphicsLayer?>(null) }
    Box(modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(alpha = if (suppressed) 0f else 1f)
                .defaultMinSize(minHeight = 38.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, colors.commentDivider, RoundedCornerShape(6.dp))
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = colors.storyDisabled.copy(alpha = 0.35f)),
                    onClick = onClick,
                    onLongClick = { onLongClick(bounds, sourceContentLayer) },
                )
                .captureSharedTransformSourceContent { sourceContentLayer = it }
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
}

fun htmlAnnotatedString(
    html: String,
    linkColor: Color,
    linkListener: LinkInteractionListener,
): AnnotatedString = runCatching {
    val document = Ksoup.parse(preserveLegacyCommentParagraphSpacing(html))
    val rendered = buildAnnotatedString {
        document.body().childNodes().forEach { node ->
            appendHtmlNode(node, linkColor, linkListener)
        }
    }
    rendered.trimmed()
}.getOrElse { AnnotatedString(Ksoup.parse(html).text()) }

private fun AnnotatedString.Builder.appendHtmlNode(
    node: Node,
    linkColor: Color,
    linkListener: LinkInteractionListener,
) {
    when (node) {
        is TextNode -> append(node.getWholeText())
        is Element -> {
            val tag = node.normalName()
            if (tag == "script" || tag == "style") return
            if (tag == "br") {
                append('\n')
                return
            }

            val start = length
            val style = htmlSpanStyle(tag)
            if (style == null) {
                node.childNodes().forEach { child -> appendHtmlNode(child, linkColor, linkListener) }
            } else {
                pushStyle(style)
                node.childNodes().forEach { child -> appendHtmlNode(child, linkColor, linkListener) }
                pop()
            }
            val end = length
            val url = node.attr("href").trim()
            if (tag == "a" && url.isNotEmpty() && start < end) {
                addLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                        linkInteractionListener = linkListener,
                    ),
                    start,
                    end,
                )
            }
        }
    }
}

private fun htmlSpanStyle(tag: String): SpanStyle? = when (tag) {
    "b", "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
    "i", "em" -> SpanStyle(fontStyle = FontStyle.Italic)
    "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
    "s", "strike", "del" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    else -> null
}

private fun AnnotatedString.trimmed(): AnnotatedString {
    val start = text.indexOfFirst { !it.isWhitespace() }
    if (start < 0) return AnnotatedString("")
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    return subSequence(start, end)
}

private fun preserveLegacyCommentParagraphSpacing(html: String): String = html
    .replace(formattedCommentParagraphStartPattern, "<br><br>")
    .replace(commentParagraphStartPattern, "<br><br>")
    .replace(commentParagraphBoundaryPattern, "</p><br><p")
    .replace(commentDivBoundaryPattern, "</div><br><div")

private val commentParagraphStartPattern = Regex("<p\\s*>", RegexOption.IGNORE_CASE)
private val formattedCommentParagraphStartPattern = Regex("[\\r\\n]+[ \\t]*<p\\s*>", RegexOption.IGNORE_CASE)
private val commentParagraphBoundaryPattern = Regex("</p>\\s*<p", RegexOption.IGNORE_CASE)
private val commentDivBoundaryPattern = Regex("</div>\\s*<div", RegexOption.IGNORE_CASE)

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

private val CommentDepthColors = listOf(
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

private val compactCommentTextStyle = TextStyle(
    textMotion = TextMotion.Static,
)
