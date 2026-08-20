package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_comment
import com.simon.harmonichackernews.resources.ic_public
import com.simon.harmonichackernews.resources.ic_whatshot
import com.simon.harmonichackernews.resources.quanta
import com.simon.harmonichackernews.resources.web_preview
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private const val ContentAnimationDuration = 220
private val ContentMotionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

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
    onLinkClick: (() -> Unit)? = null,
    onLinkLongClick: (() -> Unit)? = null,
    onCommentClick: (() -> Unit)? = null,
    onBoundsChanged: ((Rect) -> Unit)? = null,
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
    val animate = !listItem
    val dimAlpha by animateFloatAsState(
        targetValue = if (style.dimmed) 0.6f else 1f,
        animationSpec = tween(if (animate) 180 else 0),
        label = "story dim alpha",
    )
    val cardProgress by animateFloatAsState(
        targetValue = if (style.cardStyle) 1f else 0f,
        animationSpec = if (animate) contentTween() else snap(),
        label = "story card style",
    )
    val cardShape = RoundedCornerShape(8.dp)
    var previewFailed by remember(model.previewImageUrl, model.previewImageLoadFailed) {
        mutableStateOf(model.previewImageLoadFailed)
    }
    val hasPreview = !previewFailed &&
        (model.previewImageUrl != null || model.previewImageFallback != null)
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
    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = contentTween(),
        label = "story card tint",
    )

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
                    .onGloballyPositioned { onBoundsChanged?.invoke(it.boundsInWindow()) }
                    .shadow(
                        elevation = (cardProgress * 1f).dp,
                        shape = cardShape,
                        clip = false,
                    )
                    .clip(cardShape)
                    .background(background)
                    .border(
                        width = 1.dp,
                        color = colors.outlineVariant.copy(alpha = cardProgress),
                        shape = cardShape,
                    ),
            ) {
                AnimatedVisibility(
                    visible = style.previewImageMode == "large" && hasPreview,
                    enter = fadeIn(contentTween()) + expandVertically(contentTween()),
                    exit = fadeOut(contentTween()) + shrinkVertically(contentTween()),
                ) {
                    val imageInset by animateDpAsState(
                        targetValue = if (style.borderlessLargeImage) 0.dp else 10.dp,
                        animationSpec = if (animate) contentTween() else snap(),
                        label = "large story image inset",
                    )
                    val imageRadius by animateDpAsState(
                        targetValue = if (style.borderlessLargeImage) 0.dp else 8.dp,
                        animationSpec = if (animate) contentTween() else snap(),
                        label = "large story image radius",
                    )
                    StoryPreviewImage(
                        model = model,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                            .padding(start = imageInset, top = imageInset, end = imageInset)
                            .clip(RoundedCornerShape(imageRadius))
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
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val comments: @Composable () -> Unit = {
                        StoryCommentRail(
                            model = model,
                            style = style,
                            typography = typography,
                            onClick = onCommentClick,
                            animateChanges = animate,
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
                        modifier = Modifier.weight(1f),
                    )
                    if (!style.commentsOnLeft) comments()
                }
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
    onPreviewLoadSuccess: () -> Unit,
    tintBaseColorArgb: Int,
    paletteTintConfigKey: String,
    extractPreviewTint: Boolean,
    onPreviewTintExtracted: (Int) -> Unit,
    extractFaviconTint: Boolean,
    onFaviconTintExtracted: (Int) -> Unit,
    animateChanges: Boolean,
    modifier: Modifier,
) {
    val titleSize by animateFloatAsState(
        targetValue = typography.storyTitleSize,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "story title size",
    )
    val summarySize by animateFloatAsState(
        targetValue = typography.storySummarySize,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "story summary size",
    )
    val indexAlpha by animateFloatAsState(
        targetValue = if (style.showIndex) 1f else 0f,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "story index alpha",
    )
    val indexWidth by animateDpAsState(
        targetValue = if (style.showIndex) 38.dp else 0.dp,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "story index width",
    )
    val titleStartPadding by animateDpAsState(
        targetValue = if (style.showIndex) 2.dp else 4.dp,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "story title start padding",
    )
    Row(
        modifier = modifier
            .combinedClickable(
                enabled = onLinkClick != null || onLinkLongClick != null,
                onClick = { onLinkClick?.invoke() },
                onLongClick = { onLinkLongClick?.invoke() },
            )
            .padding(start = 5.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = model.index,
            modifier = Modifier
                .width(indexWidth)
                .alignBy(FirstBaseline)
                .graphicsLayer { alpha = indexAlpha },
            color = if (style.dimmed) HarmonicTheme.colors.storyDisabled
            else HarmonicTheme.colors.storyNormal,
            fontFamily = typography.family,
            fontSize = (titleSize - 1f).sp,
            textAlign = TextAlign.Center,
            style = legacyTextStyle,
        )
        StoryTextColumn(
            modifier = Modifier
                .weight(1f)
                .padding(start = titleStartPadding, end = 4.dp)
                .alignBy(FirstBaseline),
        ) {
            StoryTitleText(
                text = model.title,
                badge = model.titleBadge,
                color = if (style.dimmed) HarmonicTheme.colors.storyDisabled
                else HarmonicTheme.colors.storyNormal,
                fontFamily = typography.family,
                fontWeight = FontWeight.Bold,
                fontSize = titleSize.sp,
                style = legacyTextStyle,
            )
            AnimatedVisibility(style.showSummary && model.summary.isNotBlank()) {
                Text(
                    text = model.summary,
                    modifier = Modifier.padding(top = 3.dp),
                    color = HarmonicTheme.colors.storyDisabled,
                    fontFamily = typography.family,
                    fontSize = summarySize.sp,
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
                    tintBaseColorArgb = tintBaseColorArgb,
                    paletteTintConfigKey = paletteTintConfigKey,
                    extractTint = extractFaviconTint,
                    onTintExtracted = onFaviconTintExtracted,
                    animateChanges = animateChanges,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = hasSmallPreview,
            modifier = Modifier.align(Alignment.CenterVertically),
            enter = fadeIn(contentTween()) + expandHorizontally(contentTween()),
            exit = fadeOut(contentTween()) + shrinkHorizontally(contentTween()),
        ) {
            StoryPreviewImage(
                model = model,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(width = 72.dp, height = 52.dp)
                    .clip(RoundedCornerShape(6.dp))
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
    val metaSize by animateFloatAsState(
        targetValue = typography.storyMetaSize,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "story meta size",
    )
    val domainSuffix = model.domain
        .takeIf {
            model.domainWithoutTopLevel.isNotEmpty() &&
                it.startsWith(model.domainWithoutTopLevel)
        }
        ?.removePrefix(model.domainWithoutTopLevel)
        .orEmpty()
    val pointsVisibilityProgress = remember(model.points) {
        Animatable(if (style.showPoints) 1f else 0f)
    }
    val plusProgress = remember(model.points) {
        Animatable(if (style.compactPoints) 1f else 0f)
    }
    val pointsWordProgress = remember(model.points) {
        Animatable(if (style.compactPoints) 0f else 1f)
    }
    var renderPoints by remember(model.points) {
        mutableStateOf(style.showPoints)
    }
    LaunchedEffect(style.showPoints, animateChanges) {
        if (!animateChanges) {
            pointsVisibilityProgress.snapTo(if (style.showPoints) 1f else 0f)
            renderPoints = style.showPoints
        } else if (style.showPoints) {
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
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(style.showFavicon) {
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
            fontFamily = typography.family,
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
    onClick: (() -> Unit)?,
    animateChanges: Boolean,
) {
    val countSize by animateFloatAsState(
        targetValue = typography.storyCommentCountSize,
        animationSpec = if (animateChanges) contentTween() else snap(),
        label = "story comment count size",
    )
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
