package com.simon.harmonichackernews.ui.content

import com.simon.harmonichackernews.settings.UserAvatarOptions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fleeksoft.ksoup.nodes.Node
import coil3.compose.AsyncImage
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.CommentDepthPreferences
import com.simon.harmonichackernews.settings.CommentIndicatorThickness
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.CommentDepthPaletteCatalog
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_public
import com.simon.harmonichackernews.ui.comments.CommentActionSourceGeometry
import com.simon.harmonichackernews.ui.comments.captureCommentActionSourceContent
import com.simon.harmonichackernews.ui.common.captureSharedTransformSourceContent
import com.simon.harmonichackernews.ui.common.onSecondaryClick
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.CollectedReferenceLinks
import kotlin.math.abs
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
    val displayStyle: DisplayStyle,
    val textSize: Float,
    val collectLinks: Boolean,
    val emphasizeMeta: Boolean,
    val depthIndicatorMode: String,
    val showDivider: Boolean,
    val preferredFont: String,
    val animateChanges: Boolean = true,
    val transparentNonCardBackground: Boolean = false,
    val indicatorThickness: CommentIndicatorThickness = CommentIndicatorThickness.STANDARD,
    val roundedDepthIndicators: Boolean = false,
    val continuousDepthIndicators: Boolean = false,
    val userAvatarsEnabled: Boolean = false,
    val userAvatarOptions: UserAvatarOptions = UserAvatarOptions(),
) {
    val showOutline: Boolean get() = displayStyle == DisplayStyle.OUTLINED
    val cardStyle: Boolean get() = displayStyle == DisplayStyle.RAISED || displayStyle == DisplayStyle.OUTLINED
    val hasBackground: Boolean get() = displayStyle != DisplayStyle.FLAT
}

internal fun commentSurfaceColor(baseBackground: Color, textColor: Color, highlighted: Boolean): Color {
    val overlayAlpha = if (highlighted) {
        if (baseBackground.luminance() < 0.5f) 0.14f else 0.08f
    } else 0f
    return textColor.copy(alpha = overlayAlpha).compositeOver(baseBackground)
}

/** Preserve the legacy white label when readable, including custom and dynamic accent colors. */
internal fun commentCountContentColor(background: Color): Color =
    if (1.05f / (background.luminance() + 0.05f) >= 4.5f) Color.White else Color.Black

private class CommentItemGeometry {
    var coordinates: LayoutCoordinates? = null
    var contentLayer: GraphicsLayer? = null
    var indicatorCoordinates: LayoutCoordinates? = null
    var indicatorLayer: GraphicsLayer? = null
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
            indicatorBounds = indicatorCoordinates?.takeIf { it.isAttached }?.let {
                Rect(it.positionInWindow(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
            },
            indicatorLayer = indicatorLayer?.takeUnless(GraphicsLayer::isReleased),
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
        "The less hidden state there is, the easier it is to reason about [0]." +
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
    verticalPadding: Dp = 10.dp,
) {
    val typography = rememberContentTypography(
        preferredFont = style.preferredFont,
        commentTextSize = style.textSize,
    )
    val previewReferences = remember(model.body) {
        CollectedReferenceLinks.parse(model.body)
    }
    val hasPreviewReference = previewReferences.hasLinks()
    val previewBody = if (hasPreviewReference) previewReferences.bodyHtml else model.body
    val inlineReferenceHtml = remember(model.referenceMarker, model.referenceUrl) {
        "${model.referenceMarker} <a href=\"${model.referenceUrl}\">${model.referenceUrl}</a>"
    }
    val bodySize by animateFloatAsState(
        targetValue = typography.commentTextSize,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment preview text size",
    )
    val indicatorColor by animateColorAsState(
        targetValue = commentDepthColor(style.depthIndicatorMode, 0, model.author),
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment preview indicator color",
    )
    CommentSurface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = verticalPadding),
        style = style,
        showIndicator = style.depthIndicatorMode != "none",
        indicatorColor = indicatorColor,
        highlighted = false,
        onClick = {},
        onLongClick = {},
    ) {
        CommentMeta(
            author = model.author,
            avatarsEnabled = style.userAvatarsEnabled,
            avatarOptions = style.userAvatarOptions,
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
            animateSearchMatches = false,
            onLinkClick = {},
            onLinkLongClick = { _, _, _ -> },
        )
        if (hasPreviewReference) {
            AnimatedContent(
                targetState = style.collectLinks,
                transitionSpec = {
                    val direction = if (targetState) 1 else -1
                    val enter = fadeIn(
                        animationSpec = tween(durationMillis = 150, delayMillis = 70),
                    ) + slideInVertically(
                        animationSpec = contentTween(),
                        initialOffsetY = { height -> direction * height / 6 },
                    )
                    val exit = fadeOut(
                        animationSpec = tween(durationMillis = 90),
                    ) + slideOutVertically(
                        animationSpec = contentTween(),
                        targetOffsetY = { height -> -direction * height / 6 },
                    )
                    (enter togetherWith exit).using(
                        SizeTransform(clip = true) { _, _ -> contentTween() },
                    )
                },
                contentAlignment = Alignment.TopStart,
                label = "comment preview reference collection",
            ) { collectLinks ->
                if (collectLinks) {
                    ReferenceRow(
                        marker = model.referenceMarker,
                        label = model.referenceUrl,
                        typography = typography,
                        modifier = Modifier.padding(top = 5.dp),
                        onClick = {},
                        onLongClick = { _, _ -> },
                    )
                } else {
                    CommentBodyText(
                        html = inlineReferenceHtml,
                        modifier = Modifier.padding(top = 16.dp),
                        searchTerm = "",
                        markedColor = HarmonicTheme.colors.storyNormal,
                        fontFamily = typography.family,
                        fontSize = bodySize,
                        animateSearchMatches = false,
                        onLinkClick = {},
                        onLinkLongClick = { _, _, _ -> },
                    )
                }
            }
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
    nextCommentDepth: Int? = null,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    flattenHierarchy: Boolean = false,
    forceExpanded: Boolean = false,
    searchTerm: String = "",
    animateSearchMatches: Boolean = false,
    suppressedReferenceUrl: String? = null,
    captureActionSource: Boolean = false,
    suppressActionSource: Boolean = false,
    showActionsOnClick: Boolean = false,
    enableLongClick: Boolean = true,
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
        .coerceAtLeast(0)
    val indicatorColor by animateColorAsState(
        targetValue = commentDepthColor(style.depthIndicatorMode, indicatorIndex, comment.by.orEmpty()),
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "runtime comment indicator color",
    )
    val textCollapsed = !forceExpanded && !comment.expanded && collapseParent
    val renderModel = remember(
        comment.id,
        comment.expandedAnchorText,
        style.collectLinks,
    ) {
        CommentRenderModelCache.get(
            commentId = comment.id,
            expandedHtml = comment.expandedAnchorText,
            collectLinks = style.collectLinks,
        )
    }
    val references = renderModel.references
    val contentBlocks = renderModel.contentBlocks
    val hasInterleavedReferences = references?.hasInterleavedLinks() == true
    val firstReferenceIndex = contentBlocks.indexOfFirst { it.getLink() != null }
    val markedColor = if (colors.background.luminance() < 0.5f) Color(0xfffce205) else Color(0xffcc7722)
    val hiddenPreview = remember(comment.id, comment.text, textCollapsed) {
        collapsedCommentPreview(comment.id, comment.text, textCollapsed)
    }

    // Equal space on either side of the row boundary centers its divider between surfaces.
    val top = if (style.hasBackground) 0.dp else 8.dp
    val bottom = top
    val indicatorGeometry = animatedCommentIndicatorGeometry(style, showIndicator)
    val continuousProgress = indicatorGeometry.continuous
    val firstRailDepth = if (showTopLevelIndicator) 0 else 1
    val railColors = if (continuousProgress > 0f) (firstRailDepth..effectiveDepth).map { depth ->
        commentDepthColor(style.depthIndicatorMode, depth - firstRailDepth, "")
    } else emptyList()
    val dividerColor = colors.commentDivider
    val decorations = Modifier.drawWithCache {
        // Match the row layout's pixel rounding, including devices with fractional density.
        val railWidth = indicatorGeometry.width.roundToPx().toFloat()
        val inset = (if (style.hasBackground) 4.dp else top).roundToPx().toFloat()
        fun railStart(depth: Int): Float = min(
            16.dp.roundToPx() + 12.dp.roundToPx() * depth,
            (size.width * 0.6f).roundToInt(),
        ).toFloat()
        onDrawBehind {
            railColors.forEachIndexed { index, railColor ->
                val color = railColor.copy(alpha = railColor.alpha * continuousProgress)
                val depth = firstRailDepth + index
                val x = railStart(depth)
                // The current line belongs to CommentSurface, including its detached rail.
                // Only extend it through the gap below a parent with replies.
                val isAncestor = depth < effectiveDepth
                if (!isAncestor && (nextCommentDepth == null || nextCommentDepth <= depth)) return@forEachIndexed
                val y = if (isAncestor) 0f else size.height - inset
                val bottomY = if (nextCommentDepth != null && nextCommentDepth > depth) {
                    size.height
                } else size.height - inset
                // Only the actual branch ends are rounded; joins remain seamless.
                val radius = if (isAncestor) railWidth / 2f * indicatorGeometry.rounding else 0f
                drawRoundRect(color, Offset(x, y), Size(railWidth, (bottomY - y).coerceAtLeast(0f)), CornerRadius(radius))
                if (y == 0f) drawRect(color, Offset(x, 0f), Size(railWidth, radius))
                if (bottomY == size.height) drawRect(color, Offset(x, bottomY - radius), Size(railWidth, radius))
            }
            if (style.showDivider) {
                val x = railStart(effectiveDepth) + (railWidth + 8.dp.toPx()) * continuousProgress
                drawRect(dividerColor, Offset(x, size.height - 0.5.dp.toPx()), Size((size.width - x - 16.dp.toPx()).coerceAtLeast(0f), 1.dp.toPx()))
            }
        }
    }
    val itemGeometry = remember { CommentItemGeometry() }
    val hiddenSurfaceLayer = if (suppressActionSource) rememberGraphicsLayer() else null
    var pendingActionSourceGesture by remember {
        mutableStateOf<CommentActionSourceGesture?>(null)
    }
    fun publishActionSourceGeometry() {
        itemGeometry.snapshot()?.let { onActionSourceGeometryChanged?.invoke(it) }
    }
    LaunchedEffect(pendingActionSourceGesture) {
        val gesture = pendingActionSourceGesture ?: return@LaunchedEffect
        // Frame callbacks run before drawing. Cross a complete frame so both the card and
        // detached indicator have recorded contents before publishing the opening geometry.
        withFrameNanos { }
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
        modifier = modifier.then(decorations),
        effectiveDepth = effectiveDepth,
        cardStyle = style.hasBackground,
        topPadding = top,
        bottomPadding = bottom,
    ) {
        CommentSurface(
            modifier = Modifier.fillMaxWidth().drawWithContent {
                if (hiddenSurfaceLayer != null) {
                    // Keep captures current without hiding the surrounding thread rails.
                    hiddenSurfaceLayer.record { this@drawWithContent.drawContent() }
                } else {
                    drawContent()
                }
            },
            style = style,
            showIndicator = showIndicator,
            indicatorGeometry = indicatorGeometry,
            continuationBelow = if (nextCommentDepth != null && nextCommentDepth > effectiveDepth) {
                continuousProgress
            } else 0f,
            indicatorColor = indicatorColor,
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
            onLongClick = if (enableLongClick) {
                {
                    if (showActionsOnClick) {
                        onShowActions(itemGeometry.boundsInWindowOrNull())
                    } else {
                        pendingActionSourceGesture = CommentActionSourceGesture.LongClick
                    }
                }
            } else {
                // Keep a no-op long-click handler so a held search result is consumed as a
                // long press instead of being reinterpreted as its normal click on release.
                {}
            },
        ) {
            CommentMeta(
                author = comment.by.orEmpty(),
                avatarsEnabled = style.userAvatarsEnabled,
                avatarOptions = style.userAvatarOptions,
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
                enter = if (style.animateChanges) {
                    fadeIn(contentTween()) + expandVertically(contentTween())
                } else androidx.compose.animation.EnterTransition.None,
                exit = if (style.animateChanges) {
                    fadeOut(contentTween()) + shrinkVertically(contentTween())
                } else androidx.compose.animation.ExitTransition.None,
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
                                animateSearchMatches = animateSearchMatches,
                                onLinkClick = onLinkClick,
                                onLinkLongClick = onLinkLongClick,
                            )
                        } else {
                            ReferenceRow(
                                marker = link.markerLabel.orEmpty(),
                                label = rememberReferenceLinkLabel(link),
                                typography = typography,
                                faviconUrl = rememberReferenceLinkFaviconUrl(link),
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
    animateSearchMatches: Boolean,
    onLinkClick: (String) -> Unit,
    onLinkLongClick: (String, String, Rect) -> Unit,
) {
    val colors = HarmonicTheme.colors
    val hapticFeedback = LocalHapticFeedback.current
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
    val displayedBody = if (animateSearchMatches) {
        animatedSearchMatches(
            body = body,
            searchTerm = searchTerm,
            baseColor = colors.storyNormal,
            markedColor = markedColor,
        )
    } else {
        remember(body, searchTerm, markedColor) {
            highlightSearchMatches(body, searchTerm, markedColor)
        }
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
                    hapticFeedback = hapticFeedback,
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
private fun animatedSearchMatches(
    body: AnnotatedString,
    searchTerm: String,
    baseColor: Color,
    markedColor: Color,
): AnnotatedString {
    val normalizedSearchTerm = searchTerm.trim()
    var highlightTransition by remember(body) {
        mutableStateOf(SearchHighlightTransition.empty(body.length))
    }
    val highlightProgress = remember(body) { Animatable(0f) }
    LaunchedEffect(body, normalizedSearchTerm) {
        val currentEmphasis = highlightTransition.interpolate(highlightProgress.value)
        val targetEmphasis = searchMatchEmphasis(body.text, normalizedSearchTerm)
        highlightTransition = SearchHighlightTransition(
            from = currentEmphasis,
            to = targetEmphasis,
        )
        highlightProgress.snapTo(0f)
        if (currentEmphasis.contentEquals(targetEmphasis)) {
            highlightProgress.snapTo(1f)
        } else {
            highlightProgress.animateTo(1f, contentTween())
        }
    }
    val displayedBody = remember(
        body,
        baseColor,
        markedColor,
        highlightTransition,
        highlightProgress.value,
    ) {
        highlightSearchMatches(
            body = body,
            transition = highlightTransition,
            progress = highlightProgress.value,
            baseColor = baseColor,
            markedColor = markedColor,
        )
    }
    return displayedBody
}

@Composable
private fun animatedCommentIndicatorGeometry(
    style: CommentItemStyle,
    showIndicator: Boolean,
): CommentIndicatorGeometry {
    val width by animateDpAsState(
        if (showIndicator || style.hasBackground) style.indicatorThickness.widthDp.dp else 0.dp,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment indicator width",
    )
    val rounding by animateFloatAsState(
        if (style.roundedDepthIndicators) 1f else 0f,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment indicator rounding",
    )
    val detached by animateFloatAsState(
        if (showIndicator && style.hasBackground && style.roundedDepthIndicators) 1f else 0f,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment indicator outside card",
    )
    val continuous by animateFloatAsState(
        if (showIndicator && style.continuousDepthIndicators &&
            style.depthIndicatorMode != CommentDepthPreferences.AUTHOR
        ) 1f else 0f,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "continuous comment indicators",
    )
    return CommentIndicatorGeometry(width, rounding, detached, continuous)
}

private data class CommentIndicatorGeometry(
    val width: Dp,
    val rounding: Float,
    val detached: Float,
    val continuous: Float,
)

@Composable
private fun CommentSurface(
    modifier: Modifier,
    style: CommentItemStyle,
    showIndicator: Boolean,
    indicatorGeometry: CommentIndicatorGeometry = animatedCommentIndicatorGeometry(style, showIndicator),
    continuationBelow: Float = 0f,
    indicatorColor: Color,
    highlighted: Boolean,
    itemGeometry: CommentItemGeometry? = null,
    captureSource: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val colors = HarmonicTheme.colors
    val shapeRadius by animateDpAsState(
        if (style.hasBackground) 8.dp else 0.dp,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment corner radius",
    )
    val shape = RoundedCornerShape(
        topStart = if (showIndicator) shapeRadius * indicatorGeometry.rounding else shapeRadius,
        topEnd = shapeRadius,
        bottomEnd = shapeRadius,
        bottomStart = if (showIndicator) shapeRadius * indicatorGeometry.rounding else shapeRadius,
    )
    val baseBackground = when {
        style.hasBackground -> colors.storyCardBackground
        style.transparentNonCardBackground -> Color.Transparent
        else -> colors.settingsPageBackground
    }
    val targetBackground = commentSurfaceColor(baseBackground, colors.storyNormal, highlighted)
    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment background",
    )
    val shadowPadding by animateDpAsState(
        if (style.hasBackground) 4.dp else 0.dp,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment card padding",
    )
    val cardProgress by animateFloatAsState(
        if (style.cardStyle) 1f else 0f,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment card progress",
    )
    val outlineAlpha by animateFloatAsState(
        if (style.showOutline) 1f else 0f,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment outline",
    )
    val indicatorAlpha by animateFloatAsState(
        if (showIndicator) 1f else 0f,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment indicator",
    )
    val indicatorWidth = indicatorGeometry.width
    val indicatorRadius = if (style.hasBackground) 0.dp else indicatorWidth / 2 * indicatorGeometry.rounding
    val detachedProgress = indicatorGeometry.detached
    // Keep the rail aligned with ancestor lines while the card separates from it.
    val externalInset = (indicatorWidth + 4.dp) * detachedProgress
    val internalIndicatorWidth = indicatorWidth * (1f - detachedProgress)
    val indicatorMargin by animateDpAsState(
        if (showIndicator || style.hasBackground) {
            if (style.hasBackground) 4.dp else 8.dp
        } else 0.dp,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment indicator margin",
    )
    val contentStartPadding by animateDpAsState(
        if (style.hasBackground) 4.5.dp else 5.dp,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment content start padding",
    )
    val contentEndPadding by animateDpAsState(
        if (style.hasBackground) 8.dp else 4.dp,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment content end padding",
    )
    val contentVerticalPadding by animateDpAsState(
        if (style.hasBackground) 7.dp else 5.dp,
        animationSpec = if (style.animateChanges) contentTween() else snap(),
        label = "comment content vertical padding",
    )
    SideEffect {
        itemGeometry?.containerColor = background
        itemGeometry?.containerCornerRadiusDp = if (style.hasBackground) 8f else 0f
        itemGeometry?.containerElevationDp = if (style.cardStyle) 1f else 0f
        itemGeometry?.containerBorderColor = colors.commentDivider
        itemGeometry?.containerBorderWidthDp =
            if (style.showOutline) 1f else 0f
        if (detachedProgress == 0f) itemGeometry?.indicatorLayer = null
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
        Box(
            Modifier.fillMaxWidth().padding(shadowPadding),
        ) {
            val indicatorCaptureModifier = if (itemGeometry != null && captureSource && detachedProgress > 0f) {
                Modifier.captureCommentActionSourceContent { itemGeometry.indicatorLayer = it }
            } else {
                Modifier
            }
            if (detachedProgress > 0f) Box(
                Modifier.matchParentSize()
                    .wrapContentWidth(Alignment.Start)
                    .width(indicatorWidth)
                    .onGloballyPositioned { itemGeometry?.indicatorCoordinates = it }
                    .then(indicatorCaptureModifier)
                    .drawWithCache {
                        val width = indicatorWidth.roundToPx().toFloat()
                        val radius = indicatorWidth / 2 * indicatorGeometry.rounding
                        val outline = RoundedCornerShape(
                            topStart = radius,
                            topEnd = radius,
                            bottomStart = radius * (1f - continuationBelow),
                            bottomEnd = radius * (1f - continuationBelow),
                        ).createOutline(Size(width, size.height), layoutDirection, this)
                        onDrawBehind {
                            drawOutline(
                                outline,
                                indicatorColor.copy(alpha = indicatorColor.alpha * indicatorAlpha * detachedProgress),
                            )
                        }
                    },
            )
            Layout(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = externalInset)
                    .shadow((cardProgress * 1f).dp, shape, clip = false)
                    .clip(shape)
                    .background(background)
                    .drawWithCache {
                        val outline = shape.createOutline(size, layoutDirection, this)
                        // The outer half is clipped by the surface shape. Draw before the
                        // children so an attached depth indicator owns the full start edge,
                        // including its top and bottom pixels in the shared action transition.
                        val stroke = Stroke(2.dp.toPx())
                        onDrawBehind {
                            drawOutline(
                                outline,
                                colors.commentDivider.copy(alpha = outlineAlpha),
                                style = stroke,
                            )
                        }
                    }
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = colors.storyDisabled.copy(alpha = 0.35f)),
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                    .then(
                        onLongClick?.let { callback ->
                            Modifier.onSecondaryClick { callback() }
                        } ?: Modifier,
                    )
                    .then(sourceCaptureModifier),
                content = {
                    Box(
                        Modifier
                            .width(internalIndicatorWidth)
                            .graphicsLayer(alpha = indicatorAlpha * (1f - detachedProgress))
                            .background(
                                indicatorColor,
                                RoundedCornerShape(
                                    topStart = indicatorRadius,
                                    topEnd = indicatorRadius,
                                    bottomStart = indicatorRadius * (1f - continuationBelow),
                                    bottomEnd = indicatorRadius * (1f - continuationBelow),
                                ),
                            ),
                    )
                    Column(
                        Modifier.padding(
                            start = contentStartPadding + 4.dp * detachedProgress,
                            top = contentVerticalPadding,
                            end = contentEndPadding,
                            bottom = contentVerticalPadding,
                        ),
                    ) { content() }
                },
            ) { measurables, constraints ->
                val indicatorWidthPx = internalIndicatorWidth.roundToPx()
                    .coerceAtMost(constraints.maxWidth)
                val indicatorMarginPx = (indicatorMargin * (1f - detachedProgress)).roundToPx()
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
    }
}

@Composable
private fun CommentMeta(
    author: String,
    avatarsEnabled: Boolean,
    avatarOptions: UserAvatarOptions,
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
        AnimatedContent(
            targetState = avatarsEnabled,
            transitionSpec = {
                (fadeIn(if (animateChanges) contentTween() else snap()) togetherWith
                    fadeOut(if (animateChanges) contentTween() else snap())).using(
                    SizeTransform(clip = true) { _, _ -> if (animateChanges) contentTween() else snap() },
                )
            },
            contentAlignment = Alignment.CenterStart,
            label = "comment user avatar",
        ) { showAvatars ->
            if (!showAvatars) {
                Box(Modifier.size(0.dp))
            } else {
                UserAvatar(author, Modifier.padding(end = 6.dp).size(22.dp), avatarOptions)
            }
        }
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
                Text(
                    " • $userTag",
                    color = metaColor,
                    fontFamily = fontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
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
                    .background(colors.commentCountIndicator)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                    .then(
                        if (showHiddenReplyCount) Modifier else Modifier.clearAndSetSemantics { },
                    ),
                color = commentCountContentColor(colors.commentCountIndicator),
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
    typography: ContentTypography,
    faviconUrl: String? = null,
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
                .onSecondaryClick { onLongClick(bounds, sourceContentLayer) }
                .captureSharedTransformSourceContent { sourceContentLayer = it }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val faviconFallback = painterResource(Res.drawable.ic_public)
            if (faviconUrl == null) {
                Icon(
                    painter = faviconFallback,
                    contentDescription = null,
                    tint = colors.drawable,
                    modifier = Modifier.padding(end = 8.dp).size(17.dp),
                )
            } else {
                var faviconLoaded by remember(faviconUrl) { mutableStateOf(false) }
                AsyncImage(
                    model = faviconUrl,
                    contentDescription = null,
                    placeholder = faviconFallback,
                    error = faviconFallback,
                    fallback = faviconFallback,
                    colorFilter = if (faviconLoaded) null else ColorFilter.tint(colors.drawable),
                    onLoading = { faviconLoaded = false },
                    onSuccess = { faviconLoaded = true },
                    onError = { faviconLoaded = false },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(17.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )
            }
            if (marker.isNotBlank()) {
                Text(
                    marker,
                    modifier = Modifier.padding(end = 8.dp),
                    color = colors.storyDisabled,
                    fontFamily = typography.family,
                    fontWeight = FontWeight.Bold,
                    fontSize = typography.referenceMarkerSize.sp,
                )
            }
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = colors.storyNormal,
                fontFamily = typography.family,
                fontSize = typography.referenceLabelSize.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun highlightSearchMatches(
    body: AnnotatedString,
    searchTerm: String,
    markedColor: Color,
): AnnotatedString {
    val needle = searchTerm.trim()
    if (needle.isEmpty()) return body

    return buildAnnotatedString {
        append(body)
        var start = body.text.indexOf(needle, ignoreCase = true)
        while (start >= 0) {
            addStyle(
                SpanStyle(color = markedColor, fontWeight = FontWeight.Bold),
                start,
                start + needle.length,
            )
            start = body.text.indexOf(needle, start + needle.length, ignoreCase = true)
        }
    }
}

private data class SearchHighlightTransition(
    val from: FloatArray,
    val to: FloatArray,
) {
    fun interpolate(progress: Float): FloatArray = FloatArray(from.size) { index ->
        from[index] + (to[index] - from[index]) * progress
    }

    companion object {
        fun empty(length: Int) = SearchHighlightTransition(
            from = FloatArray(length),
            to = FloatArray(length),
        )
    }
}

internal fun searchMatchEmphasis(
    text: String,
    searchTerm: String,
): FloatArray {
    val emphasis = FloatArray(text.length)
    val needle = searchTerm.trim()
    if (needle.isEmpty()) return emphasis

    // Match in the original text: Unicode lowercasing can change its length and offsets.
    var start = text.indexOf(needle, ignoreCase = true)
    while (start >= 0) {
        for (index in start until start + needle.length) {
            emphasis[index] = 1f
        }
        start = text.indexOf(needle, start + needle.length, ignoreCase = true)
    }
    return emphasis
}

private fun highlightSearchMatches(
    body: AnnotatedString,
    transition: SearchHighlightTransition,
    progress: Float,
    baseColor: Color,
    markedColor: Color,
): AnnotatedString {
    val emphasis = transition.interpolate(progress)
    if (emphasis.all { it <= SearchHighlightThreshold }) return body

    return buildAnnotatedString {
        append(body)
        var index = 0
        while (index < emphasis.size) {
            val amount = emphasis[index]
            if (amount <= SearchHighlightThreshold) {
                index++
                continue
            }
            val baseWeight = body.baseFontWeightAt(index)
            val start = index
            index++
            while (
                index < emphasis.size &&
                emphasis[index] > SearchHighlightThreshold &&
                abs(emphasis[index] - amount) <= SearchHighlightThreshold &&
                body.baseFontWeightAt(index) == baseWeight
            ) {
                index++
            }
            val weight = (
                baseWeight.weight +
                    (FontWeight.Bold.weight - baseWeight.weight) * amount
                ).roundToInt().coerceIn(1, 1000)
            addStyle(
                SpanStyle(
                    color = lerp(baseColor, markedColor, amount),
                    fontWeight = FontWeight(weight),
                ),
                start,
                index,
            )
        }
    }
}

private fun AnnotatedString.baseFontWeightAt(index: Int): FontWeight = spanStyles
    .lastOrNull { range -> index >= range.start && index < range.end && range.item.fontWeight != null }
    ?.item
    ?.fontWeight
    ?: FontWeight.Normal

private const val SearchHighlightThreshold = 0.001f

@Composable
private fun commentDepthColor(mode: String, depth: Int, author: String): Color {
    val selection = LocalHarmonicUiDependencies.current.appearance.selection()
    return CommentDepthPaletteCatalog.color(mode, selection.theme, selection.dark, depth, author)
}

private val animatedCommentTextStyle = TextStyle(
    textMotion = TextMotion.Animated,
)

private val compactCommentTextStyle = TextStyle(
    textMotion = TextMotion.Static,
)
