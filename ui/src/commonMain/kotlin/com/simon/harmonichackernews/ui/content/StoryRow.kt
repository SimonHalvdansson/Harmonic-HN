@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_public
import com.simon.harmonichackernews.resources.quanta
import com.simon.harmonichackernews.resources.web_preview
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.common.onSecondaryClick
import com.simon.harmonichackernews.ui.stories.StoryPreviewSourceGeometry
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import org.jetbrains.compose.resources.DrawableResource
import kotlin.math.roundToInt

private val StoryCardShape = RoundedCornerShape(8.dp)

/** Avoids transition state and animated layouts for immutable story-list presentation. */
@Composable
internal fun StoryVisibility(
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

@Immutable
data class StoryRowModel(
    val index: String,
    val title: String,
    val titleBadge: StoryTitleBadge? = null,
    val previewText: String,
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
    // Decoded off-thread by the caller; do not mutate pixels after publishing the model.
    val previewImageBitmap: ImageBitmap? = null,
)

@Immutable
data class StoryRowStyle(
    val previewImageMode: StoryPreviewMode,
    val borderlessLargeImage: Boolean,
    val compact: Boolean,
    val showPreviewText: Boolean,
    val showFavicon: Boolean,
    val showPoints: Boolean,
    val compactPoints: Boolean,
    val includeTopLevelDomain: Boolean,
    val showCommentCount: Boolean,
    val showIndex: Boolean,
    val commentsOnLeft: Boolean,
    val tintCard: Boolean,
    val displayStyle: DisplayStyle,
    val useHotnessIcon: Boolean,
    val preferredFont: String,
    val textSize: Float,
    val dimmed: Boolean = false,
    val paletteTintConfigKey: String = PaletteTintPreferences.DEFAULT,
    val mediumPreviewImageHeight: Dp = 88.dp,
) {
    val showOutline: Boolean get() = displayStyle == DisplayStyle.OUTLINED
    val cardStyle: Boolean get() = displayStyle == DisplayStyle.RAISED || displayStyle == DisplayStyle.OUTLINED
    val hasBackground: Boolean get() = displayStyle != DisplayStyle.FLAT
}

val SettingsStoryPreviewModel = StoryRowModel(
    index = "3.",
    title = "Algorithm breaks speed limit for solving linear equations",
    previewText = "A faster method uses a new approach to solve large linear systems more efficiently.",
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
 * Complete platform-neutral story row. Image fetching uses Coil and palette extraction uses
 * the shared Harmonic extractor; platforms only persist resolved tint state.
 */
@Composable
fun StoryRow(
    model: StoryRowModel,
    style: StoryRowStyle,
    modifier: Modifier = Modifier,
    listItem: Boolean = false,
    animateChanges: Boolean = !listItem,
    onLinkClick: (() -> Unit)? = null,
    onLinkLongClick: (() -> Unit)? = null,
    onCommentClick: (() -> Unit)? = null,
    onGeometryChanged: ((bounds: Rect, itemHeightPx: Int) -> Unit)? = null,
    onPreviewSourceGeometryChanged: ((StoryPreviewSourceGeometry) -> Unit)? = null,
    capturePreviewSourceGeometry: Boolean = false,
    sourceAccessoryAlpha: Float = 1f,
    onPreviewLoadSuccess: (() -> Unit)? = null,
    onPreviewLoadFailed: (() -> Unit)? = null,
    onPreviewTintExtracted: ((Int) -> Unit)? = null,
    onFaviconTintExtracted: ((Int) -> Unit)? = null,
    pageBackground: Color = HarmonicTheme.colors.background,
    typographyOverride: ContentTypography? = null,
    cardPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
) {
    val colors = HarmonicTheme.colors
    val typography = typographyOverride ?: rememberContentTypography(
        preferredFont = style.preferredFont,
        storyTextSize = style.textSize,
    )
    val animate = animateChanges
    val presentation = rememberStoryRowPresentation(
        model = model,
        style = style,
        listItem = listItem,
        animate = animate,
        pageBackground = pageBackground,
        onPreviewLoadSuccess = onPreviewLoadSuccess,
        onPreviewLoadFailed = onPreviewLoadFailed,
        onPreviewTintExtracted = onPreviewTintExtracted,
        onFaviconTintExtracted = onFaviconTintExtracted,
    )
    val dimAlpha = presentation.dimAlpha
    val cardProgress = presentation.cardProgress
    val outlineAlpha = presentation.outlineAlpha
    val renderedStyle = presentation.renderedStyle
    val renderedPreviewMode = renderedStyle.previewImageMode
    val mediumPreview = renderedPreviewMode == StoryPreviewMode.MEDIUM
    val hasPreview = presentation.hasPreview
    val background = presentation.background
    val tintBaseColorArgb = presentation.tintBaseColorArgb
    val previewAvailable = renderedPreviewMode != StoryPreviewMode.OFF && hasPreview
    val handlePreviewLoadFailed = presentation.onPreviewLoadFailed
    val handlePreviewLoadSuccess = presentation.onPreviewLoadSuccess
    val handlePreviewTintExtracted = presentation.onPreviewTintExtracted
    val handleFaviconTintExtracted = presentation.onFaviconTintExtracted
    val previewCapture = rememberStoryRowPreviewCapture(
        style = renderedStyle,
        hasPreview = hasPreview,
        listItem = listItem,
        capturePreviewSourceGeometry = capturePreviewSourceGeometry,
        onGeometryChanged = onGeometryChanged,
        onPreviewSourceGeometryChanged = onPreviewSourceGeometryChanged,
        onLinkLongClick = onLinkLongClick,
    )
    val itemGeometry = previewCapture.geometry
    val captureSourceContent = previewCapture.captureContent
    val trackedLinkLongClick = previewCapture.onLongClick
    val geometryModifier = previewCapture.modifier
    val cardDecorationModifier = if (listItem && !animate) {
        when {
            style.cardStyle -> Modifier
                .shadow(elevation = 1.dp, shape = StoryCardShape, clip = false)
                .clip(StoryCardShape)
                .background(background)
                .border(
                    width = 1.dp,
                    color = colors.outlineVariant.copy(alpha = outlineAlpha),
                    shape = StoryCardShape,
                )
            style.hasBackground || style.tintCard -> Modifier
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
                color = colors.outlineVariant.copy(alpha = outlineAlpha),
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
        Box(Modifier.fillMaxWidth().padding(cardPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(geometryModifier)
                    // The children already animate their measured sizes. A second size
                    // animation here trails those measurements and clips the moving content.
                    .then(cardDecorationModifier),
            ) {
                StoryVisibility(
                    visible = renderedPreviewMode == StoryPreviewMode.LARGE && hasPreview,
                    animate = animate,
                    enter = fadeIn(contentTween()) + expandVertically(contentTween()),
                    exit = fadeOut(contentTween()) + shrinkVertically(contentTween()),
                ) {
                    StoryLargePreviewImage(
                        model = model,
                        style = style,
                        presentation = presentation,
                        animate = animate,
                        captureSourceContent = captureSourceContent,
                        itemGeometry = itemGeometry,
                        onLinkClick = onLinkClick,
                        onLinkLongClick = trackedLinkLongClick,
                    )
                }
                val animatedRailWidth = storyPreviewRailWidth(
                    renderedPreviewMode = renderedPreviewMode,
                    hasPreview = hasPreview,
                    animate = animate,
                    listItem = listItem,
                )
                val sharedPreviewImageKey = remember(
                    model.previewImageUrl,
                    model.previewImageFallback,
                    model.previewImageBitmap,
                ) { Any() }
                val storyContentRow: @Composable (SharedTransitionScope?) -> Unit =
                    { sharedTransitionScope ->
                        val mediumAccessoryAlpha: Float
                        val smallAccessoryAlpha: Float
                        if (sharedTransitionScope != null) {
                            val previewModeTransition = updateTransition(
                                targetState = mediumPreview,
                                label = "story preview image mode",
                            )
                            val animatedSmallAlpha by previewModeTransition.animateFloat(
                                transitionSpec = {
                                    if (initialState && !targetState) {
                                        tween(
                                            durationMillis = 80,
                                            delayMillis = 85,
                                            easing = ContentMotionEasing,
                                        )
                                    } else {
                                        snap()
                                    }
                                },
                                label = "small story accessories",
                            ) { isMedium ->
                                if (isMedium) 0f else 1f
                            }
                            mediumAccessoryAlpha = presentation.mediumAccessoryAlpha.value
                            smallAccessoryAlpha = animatedSmallAlpha
                        } else {
                            mediumAccessoryAlpha = 1f
                            smallAccessoryAlpha = 1f
                        }
                        val comments: @Composable () -> Unit = if (
                            sharedTransitionScope != null
                        ) {
                            {
                                Box(Modifier.fillMaxSize()) {
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = mediumPreview,
                                        modifier = Modifier.fillMaxSize(),
                                        enter = fadeIn(contentTween()),
                                        exit = fadeOut(contentTween()),
                                    ) {
                                        StoryMediumPreviewRail(
                                            model = model,
                                            style = renderedStyle,
                                            typography = typography,
                                            hasPreview = hasPreview,
                                            dimAlpha = dimAlpha,
                                            onClick = onCommentClick,
                                            onLongClick = trackedLinkLongClick,
                                            onPreviewLoadFailed = handlePreviewLoadFailed,
                                            onPreviewLoadSuccess = handlePreviewLoadSuccess,
                                            tintBaseColorArgb = tintBaseColorArgb,
                                            paletteTintConfigKey = style.paletteTintConfigKey,
                                            extractPreviewTint = style.tintCard &&
                                                model.previewImageTintArgb == null,
                                            onPreviewTintExtracted = handlePreviewTintExtracted,
                                            capturePreviewSource = captureSourceContent,
                                            itemGeometry = itemGeometry,
                                            animateChanges = true,
                                            sourceAccessoryAlpha =
                                                sourceAccessoryAlpha * mediumAccessoryAlpha,
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = this,
                                            sharedContentKey = sharedPreviewImageKey,
                                        )
                                    }
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = !mediumPreview,
                                        modifier = Modifier.fillMaxSize(),
                                        enter = fadeIn(contentTween()),
                                        exit = fadeOut(contentTween()),
                                    ) {
                                        StoryCommentRail(
                                            model = model,
                                            style = renderedStyle,
                                            typography = typography,
                                            dimAlpha = dimAlpha,
                                            onClick = onCommentClick,
                                            onLongClick = trackedLinkLongClick,
                                            animateChanges = true,
                                            modifier = Modifier
                                                .graphicsLayer(alpha = smallAccessoryAlpha)
                                                .captureStoryPreviewElement(
                                                    enabled = captureSourceContent,
                                                    onPositioned = {
                                                        itemGeometry.commentsCoordinates = it
                                                    },
                                                    onLayerChanged = {
                                                        itemGeometry.commentsLayer = it
                                                    },
                                                ),
                                        )
                                    }
                                }
                            }
                        } else if (mediumPreview) {
                            {
                                StoryMediumPreviewRail(
                                    model = model,
                                    style = renderedStyle,
                                    typography = typography,
                                    hasPreview = hasPreview,
                                    dimAlpha = dimAlpha,
                                    onClick = onCommentClick,
                                    onLongClick = trackedLinkLongClick,
                                    onPreviewLoadFailed = handlePreviewLoadFailed,
                                    onPreviewLoadSuccess = handlePreviewLoadSuccess,
                                    tintBaseColorArgb = tintBaseColorArgb,
                                    paletteTintConfigKey = style.paletteTintConfigKey,
                                    extractPreviewTint = style.tintCard &&
                                        model.previewImageTintArgb == null,
                                    onPreviewTintExtracted = handlePreviewTintExtracted,
                                    capturePreviewSource = captureSourceContent,
                                    itemGeometry = itemGeometry,
                                    animateChanges = animate,
                                    sourceAccessoryAlpha = sourceAccessoryAlpha,
                                    sharedTransitionScope = null,
                                    animatedVisibilityScope = null,
                                    sharedContentKey = null,
                                )
                            }
                        } else {
                            {
                                StoryCommentRail(
                                    model = model,
                                    style = renderedStyle,
                                    typography = typography,
                                    dimAlpha = dimAlpha,
                                    onClick = onCommentClick,
                                    onLongClick = trackedLinkLongClick,
                                    animateChanges = animate,
                                    modifier = Modifier.captureStoryPreviewElement(
                                        enabled = captureSourceContent,
                                        onPositioned = {
                                            itemGeometry.commentsCoordinates = it
                                        },
                                        onLayerChanged = {
                                            itemGeometry.commentsLayer = it
                                        },
                                    ),
                                )
                            }
                        }
                        StoryContentRow(
                            commentsOnLeft = style.commentsOnLeft && !mediumPreview,
                            animateChanges = animate,
                            railWidth = animatedRailWidth,
                            contentMinHeight = if (mediumPreview && hasPreview) {
                                if (renderedStyle.borderlessLargeImage) MediumPreviewImageMinimumHeight
                                else renderedStyle.mediumPreviewImageHeight + 16.dp
                            } else {
                                0.dp
                            },
                            comments = comments,
                        ) {
                            StoryMainContent(
                                model = model,
                                style = renderedStyle,
                                typography = typography,
                                hasSmallPreview = hasPreview &&
                                    renderedPreviewMode == StoryPreviewMode.SMALL,
                                dimAlpha = dimAlpha,
                                onLinkClick = onLinkClick,
                                onLinkLongClick = trackedLinkLongClick,
                                onPreviewLoadFailed = handlePreviewLoadFailed,
                                onPreviewLoadSuccess = handlePreviewLoadSuccess,
                                tintBaseColorArgb = tintBaseColorArgb,
                                paletteTintConfigKey = style.paletteTintConfigKey,
                                extractPreviewTint = style.tintCard &&
                                    model.previewImageTintArgb == null,
                                onPreviewTintExtracted = handlePreviewTintExtracted,
                                extractFaviconTint = style.tintCard && !previewAvailable &&
                                    model.faviconTintArgb == null,
                                onFaviconTintExtracted = handleFaviconTintExtracted,
                                animateChanges = animate,
                                capturePreviewSource = captureSourceContent,
                                itemGeometry = itemGeometry,
                                sharedTransitionScope = sharedTransitionScope,
                                sharedContentKey = sharedPreviewImageKey,
                                modifier = Modifier,
                            )
                        }
                    }
                if (animate && hasPreview && !listItem) {
                    SharedTransitionLayout {
                        storyContentRow(this)
                    }
                } else {
                    storyContentRow(null)
                }
            }
        }
    }
}

/** Measures the metric rail at its requested or intrinsic width, then matches it to the content height. */
@Composable
private fun StoryContentRow(
    commentsOnLeft: Boolean,
    animateChanges: Boolean,
    railWidth: Dp?,
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
    val minimumHeight = if (animateChanges) {
        val animatedHeight by animateDpAsState(
            targetValue = contentMinHeight,
            animationSpec = contentTween(),
            label = "story content minimum height",
        )
        animatedHeight
    } else {
        contentMinHeight
    }
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            content()
            comments()
        },
    ) { measurables, constraints ->
        val minimumRowHeight = maxOf(
            constraints.minHeight,
            minimumHeight.roundToPx(),
        ).coerceAtMost(constraints.maxHeight)
        val railWidthPx = railWidth
            ?.roundToPx()
            ?: measurables[1].maxIntrinsicWidth(constraints.maxHeight)
        val constrainedRailWidthPx = railWidthPx.coerceIn(0, constraints.maxWidth)
        val contentWidth = (constraints.maxWidth - constrainedRailWidthPx).coerceAtLeast(0)
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
                minWidth = constrainedRailWidthPx,
                maxWidth = constrainedRailWidthPx,
                minHeight = rowHeight,
                maxHeight = rowHeight,
            ),
        )

        layout(constraints.maxWidth, rowHeight) {
            val contentX = (constrainedRailWidthPx * commentsOnLeftProgress).roundToInt()
            val railX = (contentWidth * (1f - commentsOnLeftProgress)).roundToInt()
            contentPlaceable.placeRelative(contentX, 0)
            railPlaceable.placeRelative(railX, 0)
        }
    }
}

@Composable
private fun StoryMainContent(
    model: StoryRowModel,
    style: StoryRowStyle,
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
    itemGeometry: StoryRowGeometry,
    sharedTransitionScope: SharedTransitionScope?,
    sharedContentKey: Any?,
    modifier: Modifier,
) {
    val foreground = readStateForeground(
        HarmonicTheme.colors.contentPrimary,
        HarmonicTheme.colors.mutedText,
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
                onLongClick = onLinkLongClick,
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
                style = storyRowTextStyle,
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
                    style = storyRowTextStyle,
                )
                StoryVisibility(
                    visible = style.showPreviewText && model.previewText.isNotBlank(),
                    animate = animateChanges,
                    enter = fadeIn(contentTween()) + expandVertically(contentTween()),
                    exit = fadeOut(contentTween()) + shrinkVertically(contentTween()),
                ) {
                    Text(
                        text = model.previewText,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .captureStoryPreviewElement(
                                enabled = capturePreviewSource,
                                onPositioned = { itemGeometry.summaryCoordinates = it },
                                onLayerChanged = { itemGeometry.summaryLayer = it },
                            ),
                        color = HarmonicTheme.colors.mutedText,
                        fontFamily = typography.family,
                        fontSize = summarySize.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = storyRowTextStyle,
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
            if (animateChanges) {
                AnimatedVisibility(
                    visible = hasSmallPreview,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    enter = fadeIn(contentTween()) + expandHorizontally(contentTween()),
                    exit = fadeOut(contentTween()) + shrinkHorizontally(contentTween()),
                ) {
                    StorySmallPreviewImage(
                        model = model,
                        dimAlpha = dimAlpha,
                        onPreviewLoadFailed = onPreviewLoadFailed,
                        onPreviewLoadSuccess = onPreviewLoadSuccess,
                        tintBaseColorArgb = tintBaseColorArgb,
                        paletteTintConfigKey = paletteTintConfigKey,
                        extractPreviewTint = extractPreviewTint,
                        onPreviewTintExtracted = onPreviewTintExtracted,
                        capturePreviewSource = capturePreviewSource,
                        itemGeometry = itemGeometry,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = this,
                        sharedContentKey = sharedContentKey,
                    )
                }
            } else if (hasSmallPreview) {
                StorySmallPreviewImage(
                    model = model,
                    dimAlpha = dimAlpha,
                    onPreviewLoadFailed = onPreviewLoadFailed,
                    onPreviewLoadSuccess = onPreviewLoadSuccess,
                    tintBaseColorArgb = tintBaseColorArgb,
                    paletteTintConfigKey = paletteTintConfigKey,
                    extractPreviewTint = extractPreviewTint,
                    onPreviewTintExtracted = onPreviewTintExtracted,
                    capturePreviewSource = capturePreviewSource,
                    itemGeometry = itemGeometry,
                    sharedTransitionScope = null,
                    animatedVisibilityScope = null,
                    sharedContentKey = null,
                    modifier = Modifier.align(Alignment.CenterVertically),
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

internal val storyRowTextStyle = TextStyle(
    textMotion = TextMotion.Static,
)
