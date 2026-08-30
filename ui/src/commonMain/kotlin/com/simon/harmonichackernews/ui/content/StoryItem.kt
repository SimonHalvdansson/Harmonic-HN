package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_arrow_drop_up
import com.simon.harmonichackernews.resources.ic_comment
import com.simon.harmonichackernews.resources.ic_public
import com.simon.harmonichackernews.resources.ic_whatshot
import com.simon.harmonichackernews.resources.quanta
import com.simon.harmonichackernews.resources.web_preview
import com.simon.harmonichackernews.settings.StoryPreviewPreferences
import com.simon.harmonichackernews.ui.common.onSecondaryClick
import com.simon.harmonichackernews.ui.stories.StoryPreviewSourceGeometry
import com.simon.harmonichackernews.ui.stories.captureStoryPreviewSourceContent
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

private const val ContentAnimationDuration = 220
private const val DimmedStoryAlpha = 0.6f
private const val StoryMetricPillDimStrength = 0.75f
private val ContentMotionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val StoryCardShape = RoundedCornerShape(8.dp)
private val MediumPreviewImageRailWidth = 128.dp
private val MediumPreviewImageMinimumHeight = 104.dp
private val MediumNoImageFullRailWidth = 116.dp
private val MediumNoImagePointsRailWidth = 92.dp
private val MediumNoImageCommentsRailWidth = 64.dp
private val MediumNoImageIconRailWidth = 52.dp
private val MediumPreviewImageShape = RoundedCornerShape(10.dp)
private val StoryMetricPillShape = RoundedCornerShape(50)
private val StoryMetricPillHeight = 25.dp

/** Avoids transition state and animated layouts for immutable story-list presentation. */
@Composable
private fun StoryVisibility(
    visible: Boolean,
    animate: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition? = null,
    exit: ExitTransition? = null,
    content: @Composable () -> Unit,
) {
    if (animate) {
        if (enter != null && exit != null) {
            AnimatedVisibility(
                visible = visible,
                modifier = modifier,
                enter = enter,
                exit = exit,
            ) { content() }
        } else {
            AnimatedVisibility(visible = visible, modifier = modifier) { content() }
        }
    } else if (visible) {
        Box(modifier = modifier, propagateMinConstraints = true) { content() }
    }
}

private class StoryItemGeometry {
    var coordinates: LayoutCoordinates? = null
    var itemHeightPx: Int = 0
    var largeImageCoordinates: LayoutCoordinates? = null
    var smallImageCoordinates: LayoutCoordinates? = null
    var titleCoordinates: LayoutCoordinates? = null
    var summaryCoordinates: LayoutCoordinates? = null
    var metaCoordinates: LayoutCoordinates? = null
    var indexCoordinates: LayoutCoordinates? = null
    var commentsCoordinates: LayoutCoordinates? = null
    var largeImageLayer: GraphicsLayer? = null
    var smallImageLayer: GraphicsLayer? = null
    var titleLayer: GraphicsLayer? = null
    var summaryLayer: GraphicsLayer? = null
    var metaLayer: GraphicsLayer? = null
    var indexLayer: GraphicsLayer? = null
    var commentsLayer: GraphicsLayer? = null

    fun snapshot(
        style: StoryItemStyle,
        hasPreview: Boolean,
        imageCornerRadiusPx: Float,
    ): StoryPreviewSourceGeometry? {
        val containerBounds = coordinates.windowBoundsOrNull() ?: return null
        val imageCoordinates = when {
            !hasPreview || style.previewImageMode == "off" -> null
            style.previewImageMode == "large" -> largeImageCoordinates
            else -> smallImageCoordinates
        }
        val imageLayer = when {
            !hasPreview || style.previewImageMode == "off" -> null
            style.previewImageMode == "large" -> largeImageLayer
            else -> smallImageLayer
        }
        return StoryPreviewSourceGeometry(
            container = containerBounds,
            containerElevationDp = if (style.cardStyle) 1f else 0f,
            image = imageCoordinates.windowBoundsOrNull(),
            title = titleCoordinates.windowBoundsOrNull(),
            summary = summaryCoordinates
                .takeIf { style.showSummary }
                .windowBoundsOrNull(),
            meta = metaCoordinates
                .takeIf { !style.compact }
                .windowBoundsOrNull(),
            index = indexCoordinates.windowBoundsOrNull(),
            comments = commentsCoordinates.windowBoundsOrNull(),
            imageCornerRadiusPx = imageCornerRadiusPx,
            imageLayer = imageLayer?.takeUnless(GraphicsLayer::isReleased),
            titleLayer = titleLayer?.takeUnless(GraphicsLayer::isReleased),
            summaryLayer = summaryLayer?.takeUnless(GraphicsLayer::isReleased),
            metaLayer = metaLayer?.takeUnless(GraphicsLayer::isReleased),
            indexLayer = indexLayer?.takeUnless(GraphicsLayer::isReleased),
            commentsLayer = commentsLayer?.takeUnless(GraphicsLayer::isReleased),
        )
    }
}

private fun LayoutCoordinates?.windowBoundsOrNull(): Rect? =
    this
        ?.takeIf(LayoutCoordinates::isAttached)
        ?.let { coordinates ->
            val topLeft = coordinates.positionInWindow()
            Rect(
                offset = topLeft,
                size = androidx.compose.ui.geometry.Size(
                    coordinates.size.width.toFloat(),
                    coordinates.size.height.toFloat(),
                ),
            )
        }
        ?.takeIf { it.width > 0f && it.height > 0f }

@Composable
private fun Modifier.captureStoryPreviewElement(
    enabled: Boolean,
    onPositioned: (LayoutCoordinates) -> Unit,
    onLayerChanged: (GraphicsLayer) -> Unit,
): Modifier = if (enabled) {
    onGloballyPositioned(onPositioned)
        .captureStoryPreviewSourceContent(onLayerChanged)
} else {
    this
}

@Immutable
data class StoryItemUiModel(
    val index: String,
    val title: String,
    val titleBadge: StoryTitleBadge? = null,
    val summary: String,
    val points: Int,
    val domain: String,
    val domainWithoutTopLevel: String,
    val age: String,
    val commentCount: Int,
    val faviconFallback: DrawableResource = Res.drawable.ic_public,
    val tintFaviconFallback: Boolean = true,
    val previewImageFallback: DrawableResource? = null,
    val faviconUrl: String? = null,
    val previewImageUrl: String? = null,
    val previewImageLoadFailed: Boolean = false,
    val faviconTintArgb: Int? = null,
    val previewImageTintArgb: Int? = null,
    val tintFallbackArgb: Int? = null,
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
    faviconFallback = Res.drawable.quanta,
    tintFaviconFallback = false,
    previewImageFallback = Res.drawable.web_preview,
)

/**
 * Complete platform-neutral story row. Image fetching and palette extraction use Coil's and
 * KMPalette's multiplatform Compose integrations; platforms only persist resolved tint state.
 */
@Composable
fun StoryItem(
    model: StoryItemUiModel,
    style: StoryItemStyle,
    modifier: Modifier = Modifier,
    listItem: Boolean = false,
    animateChanges: Boolean = !listItem,
    onLinkClick: (() -> Unit)? = null,
    onLinkLongClick: (() -> Unit)? = null,
    onCommentClick: (() -> Unit)? = null,
    onGeometryChanged: ((bounds: Rect, itemHeightPx: Int) -> Unit)? = null,
    onPreviewSourceGeometryChanged: ((StoryPreviewSourceGeometry) -> Unit)? = null,
    capturePreviewSourceGeometry: Boolean = false,
    onPreviewLoadSuccess: (() -> Unit)? = null,
    onPreviewLoadFailed: (() -> Unit)? = null,
    onPreviewTintExtracted: ((Int) -> Unit)? = null,
    onFaviconTintExtracted: ((Int) -> Unit)? = null,
) {
    val colors = HarmonicTheme.colors
    val typography = rememberContentTypography(
        preferredFont = style.preferredFont,
        storyTextSize = style.textSize,
    )
    val animate = animateChanges
    val dimAlpha = if (animate) {
        val animatedDimAlpha by animateFloatAsState(
            targetValue = if (style.dimmed) DimmedStoryAlpha else 1f,
            animationSpec = tween(180),
            label = "story dim alpha",
        )
        animatedDimAlpha
    } else if (style.dimmed) {
        DimmedStoryAlpha
    } else {
        1f
    }
    val cardProgress = if (listItem) {
        if (style.cardStyle) 1f else 0f
    } else {
        val animatedCardProgress by animateFloatAsState(
            targetValue = if (style.cardStyle) 1f else 0f,
            animationSpec = contentTween(),
            label = "story card style",
        )
        animatedCardProgress
    }
    var previewFailed by remember(model.previewImageUrl, model.previewImageLoadFailed) {
        mutableStateOf(model.previewImageLoadFailed)
    }
    val hasPreview = !previewFailed &&
        (model.previewImageUrl != null || model.previewImageFallback != null)
    val mediumPreview = style.previewImageMode == StoryPreviewPreferences.MEDIUM
    val tintFallback = model.tintFallbackArgb?.let(::Color) ?: colors.storyCardBackground
    val tintBaseColorArgb = tintFallback.toArgb()
    var extractedPreviewTint by remember(
        model.previewImageUrl,
        model.previewImageFallback,
        tintBaseColorArgb,
        style.paletteTintConfigKey,
    ) { mutableStateOf<Int?>(null) }
    var extractedFaviconTint by remember(
        model.faviconUrl,
        model.faviconFallback,
        tintBaseColorArgb,
        style.paletteTintConfigKey,
    ) { mutableStateOf<Int?>(null) }
    val previewAvailable = style.previewImageMode != "off" && hasPreview
    val tint = if (previewAvailable) {
        (model.previewImageTintArgb ?: extractedPreviewTint)?.let(::Color)
    } else {
        (model.faviconTintArgb ?: extractedFaviconTint)?.let(::Color)
    }
    val targetBackground = when {
        style.tintCard -> tint ?: tintFallback
        style.cardStyle -> colors.surfaceContainerHigh
        else -> colors.background
    }
    // Image palette extraction finishes after a list row is first composed. Preserve the old
    // blend so an arriving preview/favicon tint does not flash into place.
    val background = if (animate) {
        val animatedBackground by animateColorAsState(
            targetValue = targetBackground,
            animationSpec = contentTween(),
            label = "story card tint",
        )
        animatedBackground
    } else {
        targetBackground
    }
    val itemGeometry = remember { StoryItemGeometry() }
    var sourceCaptureRequested by remember { mutableStateOf(false) }
    val captureSourceContent = capturePreviewSourceGeometry || sourceCaptureRequested
    val density = LocalDensity.current
    val itemVerticalPaddingPx = with(density) {
        (if (listItem) 8.dp else 28.dp).roundToPx()
    }
    val previewImageCornerRadiusPx = with(density) {
        when {
            style.previewImageMode == "small" -> 6.dp.toPx()
            style.previewImageMode == StoryPreviewPreferences.MEDIUM -> 10.dp.toPx()
            style.previewImageMode == "large" && !style.borderlessLargeImage -> 8.dp.toPx()
            else -> 0f
        }
    }
    val geometryModifier = if (
        onGeometryChanged == null && onPreviewSourceGeometryChanged == null
    ) {
        Modifier
    } else {
        Modifier
            .onSizeChanged { size ->
                val itemHeightPx = size.height + itemVerticalPaddingPx
                if (itemGeometry.itemHeightPx != itemHeightPx) {
                    itemGeometry.itemHeightPx = itemHeightPx
                    onGeometryChanged?.invoke(Rect.Zero, itemHeightPx)
                }
            }
            .onGloballyPositioned { coordinates ->
                itemGeometry.coordinates = coordinates
                // Normally defer the window transforms until a preview needs this row. Once it is
                // the pager's settled source, however, keep its published geometry aligned with
                // the list. The last pager-driven list delta can land after the page settles; a
                // one-shot snapshot would then make the dismiss transform end at the old position.
                if (capturePreviewSourceGeometry) {
                    itemGeometry.snapshot(style, hasPreview, previewImageCornerRadiusPx)
                        ?.let { onPreviewSourceGeometryChanged?.invoke(it) }
                }
            }
    }
    val trackedLinkLongClick = onLinkLongClick?.let {
        { sourceCaptureRequested = true }
    }
    LaunchedEffect(sourceCaptureRequested, onLinkLongClick) {
        if (sourceCaptureRequested) {
            // Let the newly attached recording modifiers draw once before publishing the source.
            withFrameNanos { }
            itemGeometry.coordinates
                ?.takeIf(LayoutCoordinates::isAttached)
                ?.boundsInWindow()
                ?.let { bounds ->
                    onGeometryChanged?.invoke(bounds, itemGeometry.itemHeightPx)
                }
            itemGeometry.snapshot(style, hasPreview, previewImageCornerRadiusPx)
                ?.let { onPreviewSourceGeometryChanged?.invoke(it) }
            onLinkLongClick?.invoke()
            sourceCaptureRequested = false
        }
    }
    LaunchedEffect(
        capturePreviewSourceGeometry,
        style.previewImageMode,
        style.showSummary,
        style.compact,
        hasPreview,
    ) {
        if (capturePreviewSourceGeometry) {
            itemGeometry.snapshot(style, hasPreview, previewImageCornerRadiusPx)
                ?.let { onPreviewSourceGeometryChanged?.invoke(it) }
        }
    }
    val cardDecorationModifier = if (listItem) {
        when {
            style.cardStyle -> Modifier
                .shadow(elevation = 1.dp, shape = StoryCardShape, clip = false)
                .clip(StoryCardShape)
                .background(background)
                .border(
                    width = 1.dp,
                    color = colors.outlineVariant,
                    shape = StoryCardShape,
                )
            style.tintCard -> Modifier
                .clip(StoryCardShape)
                .background(background)
            else -> Modifier
        }
    } else {
        Modifier
            .shadow(
                elevation = cardProgress.dp,
                shape = StoryCardShape,
                clip = false,
            )
            .clip(StoryCardShape)
            .background(background)
            .border(
                width = 1.dp,
                color = colors.outlineVariant.copy(alpha = cardProgress),
                shape = StoryCardShape,
            )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (listItem) 0.dp else 8.dp,
                vertical = if (listItem) 0.dp else 10.dp,
            ),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(geometryModifier)
                    .then(cardDecorationModifier)
                    .then(
                        if (animate) {
                            Modifier.animateContentSize(
                                animationSpec = contentTween(),
                                alignment = Alignment.TopStart,
                            )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                StoryVisibility(
                    visible = style.previewImageMode == "large" && hasPreview,
                    animate = animate,
                    enter = fadeIn(contentTween()) + expandVertically(contentTween()),
                    exit = fadeOut(contentTween()) + shrinkVertically(contentTween()),
                ) {
                    val imageInset = if (animate) {
                        val animatedInset by animateDpAsState(
                            targetValue = if (style.borderlessLargeImage) 0.dp else 10.dp,
                            animationSpec = contentTween(),
                            label = "large story image inset",
                        )
                        animatedInset
                    } else if (style.borderlessLargeImage) {
                        0.dp
                    } else {
                        10.dp
                    }
                    val imageRadius = if (animate) {
                        val animatedRadius by animateDpAsState(
                            targetValue = if (style.borderlessLargeImage) 0.dp else 8.dp,
                            animationSpec = contentTween(),
                            label = "large story image radius",
                        )
                        animatedRadius
                    } else if (style.borderlessLargeImage) {
                        0.dp
                    } else {
                        8.dp
                    }
                    StoryPreviewImage(
                        model = model,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                            .padding(start = imageInset, top = imageInset, end = imageInset)
                            .clip(RoundedCornerShape(imageRadius))
                            .captureStoryPreviewElement(
                                enabled = captureSourceContent,
                                onPositioned = { itemGeometry.largeImageCoordinates = it },
                                onLayerChanged = { itemGeometry.largeImageLayer = it },
                            )
                            .graphicsLayer(alpha = dimAlpha),
                        onLoadFailed = {
                            previewFailed = true
                            onPreviewLoadFailed?.invoke()
                        },
                        onLoadSuccess = { onPreviewLoadSuccess?.invoke() },
                        tintBaseColorArgb = tintBaseColorArgb,
                        paletteTintConfigKey = style.paletteTintConfigKey,
                        extractTint = style.tintCard && model.previewImageTintArgb == null,
                        onTintExtracted = { tintColor ->
                            extractedPreviewTint = tintColor
                            onPreviewTintExtracted?.invoke(tintColor)
                        },
                    )
                }
                val comments: @Composable () -> Unit = if (mediumPreview) {
                    {
                        StoryMediumPreviewRail(
                            model = model,
                            style = style,
                            typography = typography,
                            hasPreview = hasPreview,
                            dimAlpha = dimAlpha,
                            onClick = onCommentClick,
                            onPreviewLoadFailed = {
                                previewFailed = true
                                onPreviewLoadFailed?.invoke()
                            },
                            onPreviewLoadSuccess = { onPreviewLoadSuccess?.invoke() },
                            tintBaseColorArgb = tintBaseColorArgb,
                            paletteTintConfigKey = style.paletteTintConfigKey,
                            extractPreviewTint = style.tintCard &&
                                model.previewImageTintArgb == null,
                            onPreviewTintExtracted = { tintColor ->
                                extractedPreviewTint = tintColor
                                onPreviewTintExtracted?.invoke(tintColor)
                            },
                            capturePreviewSource = captureSourceContent,
                            itemGeometry = itemGeometry,
                            animateChanges = animate,
                            modifier = Modifier.captureStoryPreviewElement(
                                enabled = captureSourceContent,
                                onPositioned = { itemGeometry.commentsCoordinates = it },
                                onLayerChanged = { itemGeometry.commentsLayer = it },
                            ),
                        )
                    }
                } else {
                    {
                        StoryCommentRail(
                            model = model,
                            style = style,
                            typography = typography,
                            dimAlpha = dimAlpha,
                            onClick = onCommentClick,
                            animateChanges = animate,
                            modifier = Modifier.captureStoryPreviewElement(
                                enabled = captureSourceContent,
                                onPositioned = { itemGeometry.commentsCoordinates = it },
                                onLayerChanged = { itemGeometry.commentsLayer = it },
                            ),
                        )
                    }
                }
                val targetRailWidth = when {
                    !mediumPreview -> 60.dp
                    hasPreview -> MediumPreviewImageRailWidth
                    else -> mediumNoImageRailWidth(style)
                }
                val animatedRailWidth by animateDpAsState(
                    targetValue = targetRailWidth,
                    animationSpec = contentTween(),
                    label = "story metric rail width",
                )
                StoryContentRow(
                    commentsOnLeft = style.commentsOnLeft && !mediumPreview,
                    animateChanges = animate,
                    railWidth = if (animate) animatedRailWidth else targetRailWidth,
                    contentMinHeight = if (mediumPreview && hasPreview) {
                        MediumPreviewImageMinimumHeight
                    } else {
                        0.dp
                    },
                    comments = comments,
                ) {
                    StoryMainContent(
                        model = model,
                        style = style,
                        typography = typography,
                        hasSmallPreview = hasPreview && style.previewImageMode == "small",
                        dimAlpha = dimAlpha,
                        onLinkClick = onLinkClick,
                        onLinkLongClick = trackedLinkLongClick,
                        onPreviewLoadFailed = {
                            previewFailed = true
                            onPreviewLoadFailed?.invoke()
                        },
                        onPreviewLoadSuccess = { onPreviewLoadSuccess?.invoke() },
                        tintBaseColorArgb = tintBaseColorArgb,
                        paletteTintConfigKey = style.paletteTintConfigKey,
                        extractPreviewTint = style.tintCard && model.previewImageTintArgb == null,
                        onPreviewTintExtracted = { tintColor ->
                            extractedPreviewTint = tintColor
                            onPreviewTintExtracted?.invoke(tintColor)
                        },
                        extractFaviconTint = style.tintCard && !previewAvailable &&
                            model.faviconTintArgb == null,
                        onFaviconTintExtracted = { tintColor ->
                            extractedFaviconTint = tintColor
                            onFaviconTintExtracted?.invoke(tintColor)
                        },
                        animateChanges = animate,
                        capturePreviewSource = captureSourceContent,
                        itemGeometry = itemGeometry,
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}

/** Measures the variable story content once, then gives the fixed comment rail the same height. */
@Composable
private fun StoryContentRow(
    commentsOnLeft: Boolean,
    animateChanges: Boolean,
    railWidth: Dp,
    contentMinHeight: Dp,
    comments: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val commentsOnLeftProgress = if (animateChanges) {
        val animatedProgress by animateFloatAsState(
            targetValue = if (commentsOnLeft) 1f else 0f,
            animationSpec = contentTween(),
            label = "story comments alignment",
        )
        animatedProgress
    } else if (commentsOnLeft) {
        1f
    } else {
        0f
    }
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            content()
            comments()
        },
    ) { measurables, constraints ->
        val railWidthPx = railWidth.roundToPx().coerceAtMost(constraints.maxWidth)
        val contentWidth = (constraints.maxWidth - railWidthPx).coerceAtLeast(0)
        val minimumRowHeight = maxOf(
            constraints.minHeight,
            contentMinHeight.roundToPx(),
        ).coerceAtMost(constraints.maxHeight)
        val contentPlaceable = measurables[0].measure(
            constraints.copy(
                minWidth = contentWidth,
                maxWidth = contentWidth,
                minHeight = minimumRowHeight,
            ),
        )
        val rowHeight = contentPlaceable.height
        val railPlaceable = measurables[1].measure(
            constraints.copy(
                minWidth = railWidthPx,
                maxWidth = railWidthPx,
                minHeight = rowHeight,
                maxHeight = rowHeight,
            ),
        )

        layout(constraints.maxWidth, rowHeight) {
            val contentX = (railWidthPx * commentsOnLeftProgress).roundToInt()
            val railX = (contentWidth * (1f - commentsOnLeftProgress)).roundToInt()
            contentPlaceable.placeRelative(contentX, 0)
            railPlaceable.placeRelative(railX, 0)
        }
    }
}

@Composable
private fun StoryMediumPreviewRail(
    model: StoryItemUiModel,
    style: StoryItemStyle,
    typography: ContentTypography,
    hasPreview: Boolean,
    dimAlpha: Float,
    onClick: (() -> Unit)?,
    onPreviewLoadFailed: () -> Unit,
    onPreviewLoadSuccess: () -> Unit,
    tintBaseColorArgb: Int,
    paletteTintConfigKey: String,
    extractPreviewTint: Boolean,
    onPreviewTintExtracted: (Int) -> Unit,
    capturePreviewSource: Boolean,
    itemGeometry: StoryItemGeometry,
    animateChanges: Boolean,
    modifier: Modifier = Modifier,
) {
    val hazeState = if (hasPreview) rememberHazeState() else null
    val showPoints = style.showPoints && !style.compact
    val showCommentPill = style.showCommentCount
    val showCommentText = style.showCommentCount && !style.compact
    val railPadding = if (hasPreview) {
        PaddingValues(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
    } else {
        PaddingValues(start = 4.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)
    }
    val pointsDescription = "${model.points} ${if (model.points == 1) "point" else "points"}"
    val commentsDescription =
        "${model.commentCount} ${if (model.commentCount == 1) "comment" else "comments"}"

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                enabled = onClick != null,
                onClickLabel = "Open comments",
            ) { onClick?.invoke() }
            .padding(railPadding),
        contentAlignment = if (hasPreview) Alignment.Center else Alignment.CenterEnd,
    ) {
        if (hasPreview && hazeState != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(MediumPreviewImageShape),
            ) {
                StoryPreviewImage(
                    model = model,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                        .captureStoryPreviewElement(
                            enabled = capturePreviewSource,
                            onPositioned = { itemGeometry.smallImageCoordinates = it },
                            onLayerChanged = { itemGeometry.smallImageLayer = it },
                        )
                        .graphicsLayer(alpha = dimAlpha),
                    onLoadFailed = onPreviewLoadFailed,
                    onLoadSuccess = onPreviewLoadSuccess,
                    tintBaseColorArgb = tintBaseColorArgb,
                    paletteTintConfigKey = paletteTintConfigKey,
                    extractTint = extractPreviewTint,
                    onTintExtracted = onPreviewTintExtracted,
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StoryVisibility(
                        visible = showPoints,
                        animate = animateChanges,
                        enter = fadeIn(contentTween()) + expandHorizontally(contentTween()),
                        exit = fadeOut(contentTween()) + shrinkHorizontally(contentTween()),
                    ) {
                        StoryMetricPill(
                            icon = Res.drawable.ic_arrow_drop_up,
                            text = compactStoryMetric(model.points),
                            contentDescription = pointsDescription,
                            typography = typography,
                            dimAlpha = dimAlpha,
                            hazeState = hazeState,
                            iconSize = 24.dp,
                            iconSlotWidth = 14.dp,
                            iconScale = 1.35f,
                            startPadding = 2.dp,
                            endPadding = 10.dp,
                            iconTextSpacing = 0.dp,
                        )
                    }
                    StoryVisibility(
                        visible = showPoints && showCommentPill,
                        animate = animateChanges,
                        enter = expandHorizontally(contentTween()),
                        exit = shrinkHorizontally(contentTween()),
                    ) {
                        Box(Modifier.width(4.dp))
                    }
                    StoryVisibility(
                        visible = showCommentPill,
                        animate = animateChanges,
                        enter = fadeIn(contentTween()) + expandHorizontally(contentTween()),
                        exit = fadeOut(contentTween()) + shrinkHorizontally(contentTween()),
                    ) {
                        StoryMetricPill(
                            icon = Res.drawable.ic_comment,
                            text = model.commentCount
                                .takeIf { showCommentText }
                                ?.let(::compactStoryMetric),
                            contentDescription = if (showCommentText) {
                                commentsDescription
                            } else {
                                "Comments"
                            },
                            typography = typography,
                            dimAlpha = dimAlpha,
                            hazeState = hazeState,
                            iconSize = 12.dp,
                        )
                    }
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StoryVisibility(
                    visible = showPoints,
                    animate = animateChanges,
                    enter = fadeIn(contentTween()) + expandHorizontally(contentTween()),
                    exit = fadeOut(contentTween()) + shrinkHorizontally(contentTween()),
                ) {
                    StoryMetricPill(
                        icon = Res.drawable.ic_arrow_drop_up,
                        text = compactStoryMetric(model.points),
                        contentDescription = pointsDescription,
                        typography = typography,
                        dimAlpha = dimAlpha,
                        iconSize = 24.dp,
                        iconSlotWidth = 14.dp,
                        iconScale = 1.35f,
                        startPadding = 2.dp,
                        endPadding = 10.dp,
                        iconTextSpacing = 0.dp,
                    )
                }
                StoryVisibility(
                    visible = showPoints && showCommentPill,
                    animate = animateChanges,
                    enter = expandHorizontally(contentTween()),
                    exit = shrinkHorizontally(contentTween()),
                ) {
                    Box(Modifier.width(4.dp))
                }
                StoryVisibility(
                    visible = showCommentPill,
                    animate = animateChanges,
                    enter = fadeIn(contentTween()) + expandHorizontally(contentTween()),
                    exit = fadeOut(contentTween()) + shrinkHorizontally(contentTween()),
                ) {
                    StoryMetricPill(
                        icon = Res.drawable.ic_comment,
                        text = model.commentCount
                            .takeIf { showCommentText }
                            ?.let(::compactStoryMetric),
                        contentDescription = if (showCommentText) {
                            commentsDescription
                        } else {
                            "Comments"
                        },
                        typography = typography,
                        dimAlpha = dimAlpha,
                        iconSize = 12.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryMetricPill(
    icon: DrawableResource,
    text: String?,
    contentDescription: String,
    typography: ContentTypography,
    dimAlpha: Float,
    hazeState: HazeState? = null,
    iconSize: Dp,
    iconSlotWidth: Dp = iconSize,
    iconScale: Float = 1f,
    startPadding: Dp = 5.dp,
    endPadding: Dp = 7.dp,
    iconTextSpacing: Dp = 2.dp,
) {
    val colors = HarmonicTheme.colors
    val container = colors.surfaceContainerHighest
    val foreground = readStateForeground(
        colors.storyNormal,
        colors.storyDisabled,
        dimAlpha,
        StoryMetricPillDimStrength,
    )
    val backgroundModifier = if (hazeState == null) {
        Modifier
            .clip(StoryMetricPillShape)
            .background(container.copy(alpha = 0.92f))
            .border(1.dp, colors.outlineVariant, StoryMetricPillShape)
    } else {
        val hazeTint = container.copy(alpha = 0.60f)
        Modifier
            .clip(StoryMetricPillShape)
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = HazeBlurStyle {
                    blurRadius(4.dp)
                    colorEffects(listOf(HazeColorEffect.tint(hazeTint)))
                    noiseFactor(0f)
                    fallbackColorEffect(HazeColorEffect.tint(hazeTint))
                },
            )
    }
    Row(
        modifier = Modifier
            .then(backgroundModifier)
            .height(StoryMetricPillHeight)
            .padding(start = startPadding, end = endPadding)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(if (text == null) 0.dp else iconTextSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(iconSlotWidth),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer(scaleX = iconScale, scaleY = iconScale),
                tint = foreground,
            )
        }
        if (text != null) {
            Text(
                text = text,
                color = foreground,
                fontFamily = typography.storyMetaFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                style = legacyTextStyle,
            )
        }
    }
}

private fun mediumNoImageRailWidth(style: StoryItemStyle): Dp {
    val showPoints = style.showPoints && !style.compact
    val showCommentPill = style.showCommentCount
    val showCommentText = style.showCommentCount && !style.compact
    return when {
        showPoints && showCommentText -> MediumNoImageFullRailWidth
        showPoints && showCommentPill -> MediumNoImagePointsRailWidth
        showPoints || showCommentText -> MediumNoImageCommentsRailWidth
        showCommentPill -> MediumNoImageIconRailWidth
        else -> 0.dp
    }
}

private fun compactStoryMetric(value: Int): String {
    val count = value.coerceAtLeast(0)
    return when {
        count >= 1_000_000 -> "${(count + 500_000) / 1_000_000}m"
        count >= 10_000 -> "${(count + 500) / 1_000}k"
        count >= 1_000 -> {
            val whole = count / 1_000
            val tenth = count % 1_000 / 100
            if (tenth == 0) "${whole}k" else "$whole.${tenth}k"
        }
        else -> count.toString()
    }
}

private fun readStateForeground(
    normal: Color,
    disabled: Color,
    dimAlpha: Float,
    dimStrength: Float = 1f,
): Color {
    val dimProgress = ((1f - dimAlpha) / (1f - DimmedStoryAlpha)).coerceIn(0f, 1f)
    return lerp(normal, disabled, dimProgress * dimStrength)
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
    onPreviewLoadSuccess: () -> Unit,
    tintBaseColorArgb: Int,
    paletteTintConfigKey: String,
    extractPreviewTint: Boolean,
    onPreviewTintExtracted: (Int) -> Unit,
    extractFaviconTint: Boolean,
    onFaviconTintExtracted: (Int) -> Unit,
    animateChanges: Boolean,
    capturePreviewSource: Boolean,
    itemGeometry: StoryItemGeometry,
    modifier: Modifier,
) {
    val foreground = readStateForeground(
        HarmonicTheme.colors.storyNormal,
        HarmonicTheme.colors.storyDisabled,
        dimAlpha,
    )
    val titleSize = if (animateChanges) {
        val animatedTitleSize by animateFloatAsState(
            targetValue = typography.storyTitleSize,
            animationSpec = contentTween(),
            label = "story title size",
        )
        animatedTitleSize
    } else {
        typography.storyTitleSize
    }
    val summarySize = if (animateChanges) {
        val animatedSummarySize by animateFloatAsState(
            targetValue = typography.storySummarySize,
            animationSpec = contentTween(),
            label = "story summary size",
        )
        animatedSummarySize
    } else {
        typography.storySummarySize
    }
    val indexAlpha = if (animateChanges) {
        val animatedIndexAlpha by animateFloatAsState(
            targetValue = if (style.showIndex) 1f else 0f,
            animationSpec = contentTween(),
            label = "story index alpha",
        )
        animatedIndexAlpha
    } else if (style.showIndex) {
        1f
    } else {
        0f
    }
    val indexWidth = if (animateChanges) {
        val animatedIndexWidth by animateDpAsState(
            targetValue = if (style.showIndex) 38.dp else 0.dp,
            animationSpec = contentTween(),
            label = "story index width",
        )
        animatedIndexWidth
    } else if (style.showIndex) {
        38.dp
    } else {
        0.dp
    }
    val titleStartPadding = if (animateChanges) {
        val animatedTitleStartPadding by animateDpAsState(
            targetValue = if (style.showIndex) 1.dp else 11.dp,
            animationSpec = contentTween(),
            label = "story title start padding",
        )
        animatedTitleStartPadding
    } else if (style.showIndex) {
        1.dp
    } else {
        11.dp
    }
    Box(
        modifier = modifier
            .combinedClickable(
                enabled = onLinkClick != null || onLinkLongClick != null,
                onClick = { onLinkClick?.invoke() },
                onLongClick = { onLinkLongClick?.invoke() },
            )
            .onSecondaryClick(enabled = onLinkLongClick != null) {
                onLinkLongClick?.invoke()
            }
            .padding(start = 5.dp, end = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = model.index,
                modifier = Modifier
                    .width(indexWidth)
                    .padding(vertical = 10.dp)
                    .alignBy(FirstBaseline)
                    .captureStoryPreviewElement(
                        enabled = capturePreviewSource,
                        onPositioned = { itemGeometry.indexCoordinates = it },
                        onLayerChanged = { itemGeometry.indexLayer = it },
                    )
                    .graphicsLayer { alpha = indexAlpha },
                color = foreground,
                fontFamily = typography.family,
                fontSize = (titleSize - 1f).sp,
                textAlign = TextAlign.Center,
                style = legacyTextStyle,
            )
            StoryTextColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = titleStartPadding,
                        top = if (hasSmallPreview) 7.dp else 10.dp,
                        end = 4.dp,
                        bottom = if (hasSmallPreview) 7.dp else 10.dp,
                    )
                    .alignBy(FirstBaseline),
            ) {
                StoryTitleText(
                    text = model.title,
                    badge = model.titleBadge,
                    modifier = Modifier
                        .captureStoryPreviewElement(
                            enabled = capturePreviewSource,
                            onPositioned = { itemGeometry.titleCoordinates = it },
                            onLayerChanged = { itemGeometry.titleLayer = it },
                        ),
                    color = foreground,
                    fontFamily = typography.family,
                    fontWeight = FontWeight.Bold,
                    fontSize = titleSize.sp,
                    style = legacyTextStyle,
                )
                StoryVisibility(
                    visible = style.showSummary && model.summary.isNotBlank(),
                    animate = animateChanges,
                    enter = fadeIn(contentTween()) + expandVertically(contentTween()),
                    exit = fadeOut(contentTween()) + shrinkVertically(contentTween()),
                ) {
                    Text(
                        text = model.summary,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .captureStoryPreviewElement(
                                enabled = capturePreviewSource,
                                onPositioned = { itemGeometry.summaryCoordinates = it },
                                onLayerChanged = { itemGeometry.summaryLayer = it },
                            ),
                        color = HarmonicTheme.colors.storyDisabled,
                        fontFamily = typography.family,
                        fontSize = summarySize.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = legacyTextStyle,
                    )
                }
                StoryVisibility(
                    visible = !style.compact,
                    animate = animateChanges,
                    enter = fadeIn(contentTween()) + expandVertically(contentTween()),
                    exit = fadeOut(contentTween()) + shrinkVertically(contentTween()),
                ) {
                    StoryMeta(
                        model = model,
                        style = style,
                        typography = typography,
                        dimAlpha = dimAlpha,
                        tintBaseColorArgb = tintBaseColorArgb,
                        paletteTintConfigKey = paletteTintConfigKey,
                        extractTint = extractFaviconTint,
                        onTintExtracted = onFaviconTintExtracted,
                        animateChanges = animateChanges,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .captureStoryPreviewElement(
                                enabled = capturePreviewSource,
                                onPositioned = { itemGeometry.metaCoordinates = it },
                                onLayerChanged = { itemGeometry.metaLayer = it },
                            ),
                    )
                }
            }
            StoryVisibility(
                visible = hasSmallPreview,
                animate = animateChanges,
                modifier = Modifier.align(Alignment.CenterVertically),
                enter = fadeIn(contentTween()) + expandHorizontally(contentTween()),
                exit = fadeOut(contentTween()) + shrinkHorizontally(contentTween()),
            ) {
                StoryPreviewImage(
                    model = model,
                    modifier = Modifier
                        .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                        .size(width = 72.dp, height = 52.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .captureStoryPreviewElement(
                            enabled = capturePreviewSource,
                            onPositioned = { itemGeometry.smallImageCoordinates = it },
                            onLayerChanged = { itemGeometry.smallImageLayer = it },
                        )
                        .graphicsLayer(alpha = dimAlpha),
                    onLoadFailed = onPreviewLoadFailed,
                    onLoadSuccess = onPreviewLoadSuccess,
                    tintBaseColorArgb = tintBaseColorArgb,
                    paletteTintConfigKey = paletteTintConfigKey,
                    extractTint = extractPreviewTint,
                    onTintExtracted = onPreviewTintExtracted,
                )
            }
        }
    }
}

/** A Column that exposes its first child's baseline to a baseline-aligned parent. */
@Composable
private fun StoryTextColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = content,
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        var measuredWidth = 0
        var measuredHeight = 0
        var firstBaseline = AlignmentLine.Unspecified
        val placeables = Array(measurables.size) { index ->
            val placeable = measurables[index].measure(childConstraints)
            measuredWidth = maxOf(measuredWidth, placeable.width)
            measuredHeight += placeable.height
            if (index == 0) firstBaseline = placeable[FirstBaseline]
            placeable
        }
        val width = measuredWidth
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = measuredHeight
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        val alignmentLines = if (firstBaseline != AlignmentLine.Unspecified) {
            mapOf<AlignmentLine, Int>(FirstBaseline to firstBaseline)
        } else {
            emptyMap<AlignmentLine, Int>()
        }

        layout(width, height, alignmentLines) {
            var y = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height
            }
        }
    }
}

@Composable
private fun StoryPreviewImage(
    model: StoryItemUiModel,
    modifier: Modifier,
    onLoadFailed: () -> Unit,
    onLoadSuccess: () -> Unit,
    tintBaseColorArgb: Int,
    paletteTintConfigKey: String,
    extractTint: Boolean,
    onTintExtracted: (Int) -> Unit,
) {
    if (model.previewImageUrl != null) {
        var loaded by remember(model.previewImageUrl) { mutableStateOf(false) }
        var loadedPainter by remember(model.previewImageUrl) { mutableStateOf<Painter?>(null) }
        val extractedTint = rememberPainterPaletteTint(
            painter = loadedPainter,
            baseColorArgb = tintBaseColorArgb,
            paletteTintConfigKey = paletteTintConfigKey,
            enabled = extractTint,
        )
        LaunchedEffect(extractedTint) {
            extractedTint?.let(onTintExtracted)
        }
        val loadProgress by animateFloatAsState(
            targetValue = if (loaded) 1f else 0f,
            animationSpec = tween(240, easing = ContentMotionEasing),
            label = "story image load",
        )
        val request = rememberPaletteCompatibleImageRequest(model.previewImageUrl)
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = modifier.graphicsLayer {
                alpha = loadProgress
                scaleX = 0.94f + 0.06f * loadProgress
                scaleY = 0.94f + 0.06f * loadProgress
            },
            contentScale = ContentScale.Crop,
            onSuccess = { success ->
                loaded = true
                loadedPainter = success.painter
                onLoadSuccess()
            },
            onError = { onLoadFailed() },
        )
    } else {
        model.previewImageFallback?.let { fallback ->
            val painter = painterResource(fallback)
            val extractedTint = rememberPainterPaletteTint(
                painter = painter,
                baseColorArgb = tintBaseColorArgb,
                paletteTintConfigKey = paletteTintConfigKey,
                enabled = extractTint,
            )
            LaunchedEffect(extractedTint) {
                extractedTint?.let(onTintExtracted)
            }
            Image(
                painter = painter,
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
    tintBaseColorArgb: Int,
    paletteTintConfigKey: String,
    extractTint: Boolean,
    onTintExtracted: (Int) -> Unit,
    animateChanges: Boolean,
    modifier: Modifier,
) {
    val showMetaPoints = style.showPoints &&
        style.previewImageMode != StoryPreviewPreferences.MEDIUM
    val domainSuffix = model.domain
        .takeIf {
            model.domainWithoutTopLevel.isNotEmpty() &&
                it.startsWith(model.domainWithoutTopLevel)
        }
        ?.removePrefix(model.domainWithoutTopLevel)
        .orEmpty()
    if (!animateChanges) {
        val metaText = remember(
            model.points,
            model.domainWithoutTopLevel,
            domainSuffix,
            model.age,
            showMetaPoints,
            style.compactPoints,
            style.includeTopLevelDomain,
        ) {
            AnnotatedString(
                buildString {
                    if (showMetaPoints) {
                        if (style.compactPoints) append('+')
                        append(model.points)
                        if (!style.compactPoints) append(" points")
                        append(" • ")
                    }
                    append(model.domainWithoutTopLevel)
                    if (style.includeTopLevelDomain) append(domainSuffix)
                    append(" • ")
                    append(model.age)
                },
            )
        }
        StoryMetaRow(
            model = model,
            style = style,
            typography = typography,
            dimAlpha = dimAlpha,
            tintBaseColorArgb = tintBaseColorArgb,
            paletteTintConfigKey = paletteTintConfigKey,
            extractTint = extractTint,
            onTintExtracted = onTintExtracted,
            metaText = metaText,
            metaSize = typography.storyMetaSize,
            animateChanges = animateChanges,
            modifier = modifier,
        )
        return
    }
    val metaSize by animateFloatAsState(
        targetValue = typography.storyMetaSize,
        animationSpec = contentTween(),
        label = "story meta size",
    )
    val pointsVisibilityProgress = remember(model.points) {
        Animatable(if (showMetaPoints) 1f else 0f)
    }
    val plusProgress = remember(model.points) {
        Animatable(if (style.compactPoints) 1f else 0f)
    }
    val pointsWordProgress = remember(model.points) {
        Animatable(if (style.compactPoints) 0f else 1f)
    }
    var renderPoints by remember(model.points) {
        mutableStateOf(showMetaPoints)
    }
    LaunchedEffect(showMetaPoints, animateChanges) {
        if (!animateChanges) {
            pointsVisibilityProgress.snapTo(if (showMetaPoints) 1f else 0f)
            renderPoints = showMetaPoints
        } else if (showMetaPoints) {
            renderPoints = true
            pointsVisibilityProgress.animateTo(1f, contentTween())
        } else {
            pointsVisibilityProgress.animateTo(0f, contentTween())
            renderPoints = false
        }
    }
    LaunchedEffect(style.compactPoints, animateChanges) {
        if (!animateChanges) {
            plusProgress.snapTo(if (style.compactPoints) 1f else 0f)
            pointsWordProgress.snapTo(if (style.compactPoints) 0f else 1f)
        } else if (style.compactPoints) {
            launch { plusProgress.animateTo(1f, contentTween()) }
            launch { pointsWordProgress.animateTo(0f, contentTween()) }
        } else {
            launch { plusProgress.animateTo(0f, contentTween()) }
            launch { pointsWordProgress.animateTo(1f, contentTween()) }
        }
    }
    val targetIncludesTopLevelDomain = style.includeTopLevelDomain && domainSuffix.isNotEmpty()
    val topLevelDomainProgress = remember(domainSuffix) {
        Animatable(if (targetIncludesTopLevelDomain) 1f else 0f)
    }
    var renderTopLevelDomain by remember(domainSuffix) {
        mutableStateOf(targetIncludesTopLevelDomain)
    }
    LaunchedEffect(targetIncludesTopLevelDomain, animateChanges) {
        if (!animateChanges) {
            topLevelDomainProgress.snapTo(if (targetIncludesTopLevelDomain) 1f else 0f)
            renderTopLevelDomain = targetIncludesTopLevelDomain
        } else if (targetIncludesTopLevelDomain) {
            // Keep the full string in one Text while the TLD fades in so wrapping is based on
            // the final content instead of a row of independently measured text fragments.
            renderTopLevelDomain = true
            topLevelDomainProgress.animateTo(1f, contentTween())
        } else {
            topLevelDomainProgress.animateTo(0f, contentTween())
            renderTopLevelDomain = false
        }
    }
    val metaText = buildAnnotatedString {
        if (renderPoints) {
            val pointsVisibility = pointsVisibilityProgress.value
            val plusVisibility = pointsVisibility * plusProgress.value
            append("+")
            addStyle(
                SpanStyle(
                    color = HarmonicTheme.colors.storyDisabled.copy(alpha = plusVisibility),
                    textGeometricTransform = TextGeometricTransform(
                        scaleX = plusVisibility.coerceAtLeast(0.001f),
                    ),
                ),
                start = length - 1,
                end = length,
            )
            val pointsNumberStart = length
            append(model.points.toString())
            addStyle(
                SpanStyle(
                    color = HarmonicTheme.colors.storyDisabled.copy(alpha = pointsVisibility),
                    textGeometricTransform = TextGeometricTransform(
                        scaleX = pointsVisibility.coerceAtLeast(0.001f),
                    ),
                ),
                start = pointsNumberStart,
                end = length,
            )
            val pointsWordVisibility = pointsVisibility * pointsWordProgress.value
            val pointsWordStart = length
            append(" points")
            addStyle(
                SpanStyle(
                    color = HarmonicTheme.colors.storyDisabled.copy(alpha = pointsWordVisibility),
                    textGeometricTransform = TextGeometricTransform(
                        scaleX = pointsWordVisibility.coerceAtLeast(0.001f),
                    ),
                ),
                start = pointsWordStart,
                end = length,
            )
            val separatorStart = length
            append(" • ")
            addStyle(
                SpanStyle(
                    color = HarmonicTheme.colors.storyDisabled.copy(alpha = pointsVisibility),
                    textGeometricTransform = TextGeometricTransform(
                        scaleX = pointsVisibility.coerceAtLeast(0.001f),
                    ),
                ),
                start = separatorStart,
                end = length,
            )
        }
        append(model.domainWithoutTopLevel)
        if (renderTopLevelDomain) {
            val suffixStart = length
            append(domainSuffix)
            addStyle(
                SpanStyle(
                    color = HarmonicTheme.colors.storyDisabled.copy(
                        alpha = topLevelDomainProgress.value,
                    ),
                    textGeometricTransform = TextGeometricTransform(
                        scaleX = topLevelDomainProgress.value.coerceAtLeast(0.001f),
                    ),
                ),
                start = suffixStart,
                end = length,
            )
        }
        append(" • ${model.age}")
    }
    StoryMetaRow(
        model = model,
        style = style,
        typography = typography,
        dimAlpha = dimAlpha,
        tintBaseColorArgb = tintBaseColorArgb,
        paletteTintConfigKey = paletteTintConfigKey,
        extractTint = extractTint,
        onTintExtracted = onTintExtracted,
        metaText = metaText,
        metaSize = metaSize,
        animateChanges = animateChanges,
        modifier = modifier,
    )
}

@Composable
private fun StoryMetaRow(
    model: StoryItemUiModel,
    style: StoryItemStyle,
    typography: ContentTypography,
    dimAlpha: Float,
    tintBaseColorArgb: Int,
    paletteTintConfigKey: String,
    extractTint: Boolean,
    onTintExtracted: (Int) -> Unit,
    metaText: AnnotatedString,
    metaSize: Float,
    animateChanges: Boolean,
    modifier: Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StoryVisibility(visible = style.showFavicon, animate = animateChanges) {
            if (model.faviconUrl != null) {
                var loaded by remember(model.faviconUrl) { mutableStateOf(false) }
                var failed by remember(model.faviconUrl) { mutableStateOf(false) }
                var loadedImage by remember(model.faviconUrl) { mutableStateOf<coil3.Image?>(null) }
                var loadedPainter by remember(model.faviconUrl) { mutableStateOf<Painter?>(null) }
                val extractedTint = rememberCoilImagePaletteTint(
                    image = loadedImage,
                    fallbackPainter = loadedPainter,
                    baseColorArgb = tintBaseColorArgb,
                    paletteTintConfigKey = paletteTintConfigKey,
                    enabled = extractTint,
                    sharedCacheKey = model.faviconUrl,
                )
                LaunchedEffect(extractedTint) {
                    extractedTint?.let(onTintExtracted)
                }
                val loadAlpha by animateFloatAsState(
                    targetValue = if (loaded) 1f else 0f,
                    animationSpec = contentTween(),
                    label = "story favicon load",
                )
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(17.dp)
                        .clip(RoundedCornerShape(3.dp)),
                ) {
                    Icon(
                        painter = painterResource(model.faviconFallback),
                        contentDescription = null,
                        tint = HarmonicTheme.colors.drawable,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                alpha = dimAlpha * if (failed) 1f else 1f - loadAlpha,
                            ),
                    )
                    if (!failed) {
                        val request = rememberPaletteCompatibleImageRequest(model.faviconUrl)
                        AsyncImage(
                            model = request,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(alpha = dimAlpha * loadAlpha),
                            onSuccess = { success ->
                                loaded = true
                                loadedImage = success.result.image
                                loadedPainter = success.painter
                            },
                            onError = { failed = true },
                        )
                    }
                }
            } else {
                val fallbackPainter = painterResource(model.faviconFallback)
                val extractedTint = rememberPainterPaletteTint(
                    painter = fallbackPainter,
                    baseColorArgb = tintBaseColorArgb,
                    paletteTintConfigKey = paletteTintConfigKey,
                    enabled = extractTint && !model.tintFaviconFallback,
                )
                LaunchedEffect(extractedTint) {
                    extractedTint?.let(onTintExtracted)
                }
                if (model.tintFaviconFallback) {
                    Icon(
                        painter = fallbackPainter,
                        contentDescription = null,
                        tint = HarmonicTheme.colors.drawable,
                        modifier = Modifier.padding(end = 4.dp).size(17.dp),
                    )
                } else {
                    Image(
                        painter = fallbackPainter,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp).size(17.dp),
                    )
                }
            }
        }
        Text(
            text = metaText,
            modifier = Modifier.weight(1f),
            color = HarmonicTheme.colors.storyDisabled,
            fontFamily = typography.storyMetaFamily,
            fontSize = metaSize.sp,
            style = legacyTextStyle,
        )
    }
}

@Composable
private fun rememberPaletteCompatibleImageRequest(url: String): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(context, url) {
        ImageRequest.Builder(context)
            .data(url)
            .paletteCompatible()
            .build()
    }
}

@Composable
private fun StoryCommentRail(
    model: StoryItemUiModel,
    style: StoryItemStyle,
    typography: ContentTypography,
    dimAlpha: Float,
    onClick: (() -> Unit)?,
    animateChanges: Boolean,
    modifier: Modifier = Modifier,
) {
    val foreground = readStateForeground(
        HarmonicTheme.colors.storyNormal,
        HarmonicTheme.colors.storyDisabled,
        dimAlpha,
    )
    val countSize = if (animateChanges) {
        val animatedCountSize by animateFloatAsState(
            targetValue = typography.storyCommentCountSize,
            animationSpec = contentTween(),
            label = "story comment count size",
        )
        animatedCountSize
    } else {
        typography.storyCommentCountSize
    }
    Column(
        modifier = Modifier
            .width(60.dp)
            .fillMaxHeight()
            .then(modifier)
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
            tint = HarmonicTheme.colors.drawable.copy(alpha = dimAlpha),
        )
        StoryVisibility(
            visible = style.showCommentCount && !style.compact,
            animate = animateChanges,
        ) {
            Text(
                text = model.commentCount.toString(),
                color = foreground,
                fontFamily = typography.family,
                fontWeight = FontWeight.Bold,
                fontSize = countSize.sp,
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
