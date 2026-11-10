@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.simon.harmonichackernews.ui.content

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_public
import com.simon.harmonichackernews.resources.quanta
import com.simon.harmonichackernews.resources.web_preview
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.common.onSecondaryClick
import com.simon.harmonichackernews.ui.stories.StoryPreviewSourceGeometry
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
    val categoryLabels: List<String> = emptyList(),
    val categoryIds: List<String> = emptyList(),
    val categoryInspection: StoryCategoryInspection? = null,
    val authorName: String = "",
    val authorTag: String = "",
    val authorTagColor: com.simon.harmonichackernews.settings.UserTagColor = com.simon.harmonichackernews.settings.UserTagColor.Theme,
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
    /** The signed-in user has commented on this story. */
    val commented: Boolean = false,
    val paletteTintConfigKey: String = PaletteTintPreferences.DEFAULT,
    val mediumPreviewImageHeight: Dp = 88.dp,
    val spacingScale: Float = 1f,
    val showDivider: Boolean = false,
    val transparentNonCardBackground: Boolean = false,
    val categoryTagLocation: com.simon.harmonichackernews.settings.CategoryTagLocation = com.simon.harmonichackernews.settings.CategoryTagLocation.TITLE,
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
    pageBackground: Color = MaterialTheme.colorScheme.pageBackground,
    typographyOverride: ContentTypography? = null,
    cardPadding: PaddingValues? = null,
    inspectCategories: Boolean = false,
    placementAnimation: StoryRowPlacementAnimation? = null,
) {
    val colors = MaterialTheme.colorScheme
    val typography = typographyOverride ?: rememberContentTypography(
        preferredFont = style.preferredFont,
        storyTextSize = style.textSize,
    )
    // List rows retain content-arrival motion; the settings previews own style transitions.
    val animate = animateChanges && !listItem
    val spacingScale = if (animateChanges) {
        val animated by animateFloatAsState(style.spacingScale, contentTween(), label = "story spacing")
        animated
    } else style.spacingScale
    val dividerAlpha = if (animateChanges) {
        val animated by animateFloatAsState(if (style.showDivider) 1f else 0f, contentTween(), label = "story divider")
        animated
    } else if (style.showDivider) 1f else 0f
    val renderedCardPadding = cardPadding ?: run {
        val targetHorizontalPadding = if (style.hasBackground || style.tintCard) 12.dp else 8.dp
        val horizontalPadding = if (animate) {
            val animatedPadding by animateDpAsState(
                targetValue = targetHorizontalPadding,
                animationSpec = contentTween(),
                label = "story card padding",
            )
            animatedPadding
        } else {
            targetHorizontalPadding
        }
        PaddingValues(horizontal = horizontalPadding, vertical = 4.dp * spacingScale)
    }
    val presentation = rememberStoryRowPresentation(
        model = model,
        style = style,
        listItem = listItem,
        animate = animateChanges || placementAnimation != null,
        animatePreviewPlacement = animateChanges && placementAnimation == null,
        pageBackground = pageBackground,
        onPreviewLoadSuccess = onPreviewLoadSuccess,
        onPreviewLoadFailed = onPreviewLoadFailed,
        onPreviewTintExtracted = onPreviewTintExtracted,
        onFaviconTintExtracted = onFaviconTintExtracted,
    )
    val dimAlpha = presentation.dimAlpha
    val cardProgress = presentation.cardProgress
    val outlineAlpha = presentation.outlineAlpha
    val renderedStyle = presentation.renderedStyle.copy(spacingScale = spacingScale)
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
                .drawBehind { drawRect(background.value) }
                .border(
                    width = 1.dp,
                    color = colors.outlineVariant.copy(alpha = outlineAlpha),
                    shape = StoryCardShape,
                )
            style.hasBackground || style.tintCard -> Modifier
                .clip(StoryCardShape)
                .drawBehind { drawRect(background.value) }
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
            .drawBehind { drawRect(background.value) }
            .border(
                width = 1.dp,
                color = colors.outlineVariant.copy(alpha = outlineAlpha),
                shape = StoryCardShape,
            )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (dividerAlpha > 0f) {
                    val inset = 16.dp.toPx()
                    drawRect(
                        colors.outlineVariant.copy(alpha = dividerAlpha),
                        Offset(inset, size.height - 1.dp.toPx()),
                        Size((size.width - inset * 2).coerceAtLeast(0f), 1.dp.toPx()),
                    )
                }
            }
            .padding(
                horizontal = if (listItem) 0.dp else 8.dp,
                vertical = if (listItem) 0.dp else 10.dp,
            ),
    ) {
        Box(Modifier.fillMaxWidth().padding(renderedCardPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(geometryModifier)
                    .then(cardDecorationModifier)
                    // Settings resolves the destination layout once, then moves its content
                    // and resizes this surface together. Feed rows keep content-arrival motion.
                    .then(if (placementAnimation != null) Modifier.animateContentSize(
                        tween(StorySettingsMotionDurationMillis, easing = ContentMotionEasing)) else Modifier),
            ) {
                StoryVisibility(
                    visible = renderedPreviewMode == StoryPreviewMode.LARGE && hasPreview,
                    animate = animateChanges,
                    enter = fadeIn(contentTween()) + expandVertically(contentTween()),
                    exit = fadeOut(contentTween()) + shrinkVertically(contentTween()),
                ) {
                    StoryLargePreviewImage(
                        placementAnimation = placementAnimation,
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
                                            placementAnimation = placementAnimation,
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
                                            placementAnimation = placementAnimation,
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
                                    placementAnimation = placementAnimation,
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
                                    placementAnimation = placementAnimation,
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
                            onLinkClick = onLinkClick,
                            onLinkLongClick = trackedLinkLongClick,
                            commentsOnLeft = style.commentsOnLeft && !mediumPreview,
                            animateChanges = animateChanges,
                            railWidth = animatedRailWidth,
                            contentMinHeight = if (mediumPreview && hasPreview) {
                                if (renderedStyle.borderlessLargeImage) MediumPreviewImageMinimumHeight
                                else renderedStyle.mediumPreviewImageHeight + 16.dp
                            } else {
                                0.dp
                            },
                            comments = comments,
                            smallPreview = if (hasPreview && renderedPreviewMode == StoryPreviewMode.SMALL && categoriesWithMetadata(model, style)) ({
                                Box {
                                    StorySmallPreviewImage(model = model, dimAlpha = dimAlpha,
                                        placementAnimation = placementAnimation,
                                        onPreviewLoadFailed = handlePreviewLoadFailed, onPreviewLoadSuccess = handlePreviewLoadSuccess,
                                        tintBaseColorArgb = tintBaseColorArgb, paletteTintConfigKey = style.paletteTintConfigKey,
                                        extractPreviewTint = style.tintCard && model.previewImageTintArgb == null,
                                        onPreviewTintExtracted = handlePreviewTintExtracted, capturePreviewSource = captureSourceContent,
                                        itemGeometry = itemGeometry, sharedTransitionScope = null,
                                        animatedVisibilityScope = null, sharedContentKey = null)
                                }
                            }) else null,
                            metadata = if (categoriesWithMetadata(model, style)) ({
                            Column(Modifier.fillMaxWidth()
                                .testTag("story-details")
                                .padding(start = if (style.showIndex) 43.dp else 16.dp, end = 9.dp, bottom = 10.dp * spacingScale)) {
                                val categoryColors = storyCategoryColors(if (style.tintCard) ({ background.value }) else null)
                                FlowRow(Modifier.fillMaxWidth()
                                    .captureStoryPreviewElement(enabled = captureSourceContent,
                                        onPositioned = { itemGeometry.metaCoordinates = it },
                                        onLayerChanged = { itemGeometry.metaLayer = it }),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                                    if (!style.compact) StoryRowMovingContent(placementAnimation, "metadata",
                                        modifier = Modifier.alignBy(FirstBaseline)) { metadataModifier -> StoryMeta(
                                        placementAnimation = placementAnimation,
                                        model = model, style = renderedStyle, typography = typography, dimAlpha = dimAlpha,
                                        tintBaseColorArgb = tintBaseColorArgb, paletteTintConfigKey = style.paletteTintConfigKey,
                                        extractTint = style.tintCard && !previewAvailable && model.faviconTintArgb == null,
                                        onTintExtracted = handleFaviconTintExtracted, animateChanges = animateChanges, wrapContent = true,
                                        modifier = metadataModifier,
                                    ) }
                                    model.categoryLabels.forEachIndexed { index, label ->
                                        StoryRowMovingContent(placementAnimation, "category-$label",
                                            modifier = Modifier.alignBy(FirstBaseline)) { categoryModifier ->
                                            CategoryLabelText(label, categoryColors,
                                                categoryModifier.animateStoryRowTextSize(placementAnimation, storyCategoryFontSize(style.textSize).value)
                                                    .graphicsLayer { alpha = storyCategoryAlpha(dimAlpha) },
                                                model.categoryIds.getOrNull(index), storyCategoryFontSize(style.textSize))
                                        }
                                    }
                                }
                                StoryAuthorTag(model, typography, if (style.tintCard) ({ background.value }) else null,
                                    Modifier.captureStoryPreviewElement(captureSourceContent,
                                        onPositioned = { itemGeometry.authorCoordinates = it },
                                        onLayerChanged = { itemGeometry.authorLayer = it })
                                        .graphicsLayer { alpha = dimAlpha })
                            }
                            }) else null,
                        ) {
                            StoryMainContent(
                                placementAnimation = placementAnimation,
                                inspectCategories = inspectCategories,
                                tintedSurface = if (style.tintCard) ({ background.value }) else null,
                                model = model,
                                style = renderedStyle,
                                typography = typography,
                                hasSmallPreview = hasPreview &&
                                    renderedPreviewMode == StoryPreviewMode.SMALL && !categoriesWithMetadata(model, style),
                                dimAlpha = dimAlpha,
                                onLinkClick = onLinkClick.takeUnless { categoriesWithMetadata(model, style) },
                                onLinkLongClick = trackedLinkLongClick.takeUnless { categoriesWithMetadata(model, style) },
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
                                animateContentChanges = animateChanges,
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
    onLinkClick: (() -> Unit)?,
    onLinkLongClick: (() -> Unit)?,
    commentsOnLeft: Boolean,
    animateChanges: Boolean,
    railWidth: Dp?,
    contentMinHeight: Dp,
    comments: @Composable () -> Unit,
    metadata: (@Composable () -> Unit)? = null,
    smallPreview: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val linkInteractions = remember { MutableInteractionSource() }
    val linkFeedback = remember { StoryLinkFeedback() }
    val linkModifier = if (metadata != null) Modifier
        .testTag("story-link-area")
        .combinedClickable(
            interactionSource = linkInteractions, indication = null,
            enabled = onLinkClick != null || onLinkLongClick != null,
            onClick = { onLinkClick?.invoke() }, onLongClick = onLinkLongClick,
        )
        .onSecondaryClick(enabled = onLinkLongClick != null) { onLinkLongClick?.invoke() }
    else Modifier
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
        modifier = Modifier.fillMaxWidth().then(linkModifier),
        content = {
            content()
            comments()
            if (metadata != null) Box { metadata() }
            if (smallPreview != null) Box { smallPreview() }
            if (metadata != null) Box(Modifier.clipStoryLinkFeedback(linkFeedback).indication(linkInteractions, ripple()))
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
        val smallWidth = if (smallPreview != null) 76.dp.roundToPx().coerceAtMost(
            constraints.maxWidth - constrainedRailWidthPx) else 0
        val contentWidth = (constraints.maxWidth - constrainedRailWidthPx - smallWidth).coerceAtLeast(0)
        val metadataMeasurable = if (metadata != null) measurables[2] else null
        val inlineMetadata = metadataMeasurable != null &&
            metadataMeasurable.maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity) <= contentWidth
        val metadataWidth = if (inlineMetadata) contentWidth else constraints.maxWidth
        val metadataPlaceable = metadataMeasurable?.measure(constraints.copy(
            minWidth = metadataWidth, maxWidth = metadataWidth, minHeight = 0))
        val contentPlaceable = measurables[0].measure(constraints.copy(
            minWidth = contentWidth, maxWidth = contentWidth,
            minHeight = if (inlineMetadata) 0 else minimumRowHeight))
        val smallPlaceable = if (smallPreview != null) measurables[if (metadata != null) 3 else 2]
            .measure(constraints.copy(minWidth = smallWidth, maxWidth = smallWidth, minHeight = 0)) else null
        val rowHeight = maxOf(minimumRowHeight, smallPlaceable?.height ?: 0,
            contentPlaceable.height + if (inlineMetadata) metadataPlaceable!!.height else 0)
        val railPlaceable = measurables[1].measure(constraints.copy(
            minWidth = constrainedRailWidthPx, maxWidth = constrainedRailWidthPx,
            minHeight = rowHeight, maxHeight = rowHeight))
        val totalHeight = rowHeight + if (inlineMetadata) 0 else (metadataPlaceable?.height ?: 0)
        val feedbackPlaceable = if (metadata != null) measurables.last().measure(
            androidx.compose.ui.unit.Constraints.fixed(constraints.maxWidth, totalHeight)) else null
        layout(constraints.maxWidth, totalHeight) {
            val contentX = (constrainedRailWidthPx * commentsOnLeftProgress).roundToInt()
            linkFeedback.main = Rect(contentX.toFloat(), 0f, (contentX + contentWidth + smallWidth).toFloat(), rowHeight.toFloat())
            linkFeedback.footer = if (metadataPlaceable != null && !inlineMetadata)
                Rect(0f, rowHeight.toFloat(), constraints.maxWidth.toFloat(), totalHeight.toFloat()) else null
            val railX = ((contentWidth + smallWidth) * (1f - commentsOnLeftProgress)).roundToInt()
            val contentY = if (inlineMetadata) ((rowHeight - contentPlaceable.height - metadataPlaceable!!.height) / 2).coerceAtLeast(0) else 0
            contentPlaceable.placeRelative(contentX, contentY)
            railPlaceable.placeRelative(railX, 0)
            smallPlaceable?.placeRelative(contentX + contentWidth, (rowHeight - smallPlaceable.height) / 2)
            metadataPlaceable?.placeRelative(if (inlineMetadata) contentX else 0,
                if (inlineMetadata) contentY + contentPlaceable.height else rowHeight)
            feedbackPlaceable?.placeRelative(0, 0)
        }
    }
}

@Composable
private fun StoryMainContent(
    placementAnimation: StoryRowPlacementAnimation?,
    inspectCategories: Boolean,
    tintedSurface: (() -> Color)?,
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
    animateContentChanges: Boolean,
    capturePreviewSource: Boolean,
    itemGeometry: StoryRowGeometry,
    sharedTransitionScope: SharedTransitionScope?,
    sharedContentKey: Any?,
    modifier: Modifier,
) {
    val foreground = MaterialTheme.colorScheme.onSurface.copy(alpha = dimAlpha)
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
            targetValue = if (style.showIndex) 0.dp else 11.dp,
            animationSpec = contentTween(),
            label = "story title start padding",
        )
        animatedTitleStartPadding
    } else if (style.showIndex) {
        0.dp
    } else {
        11.dp
    }
    Box(
        modifier = modifier
            .then(if (onLinkClick != null || onLinkLongClick != null) Modifier.combinedClickable(
                onClick = { onLinkClick?.invoke() },
                onLongClick = onLinkLongClick,
            ) else Modifier)
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
                    .animateStoryRowPlacement(placementAnimation)
                    .animateStoryRowTextSize(placementAnimation, titleSize - 1f)
                    .storySettingsAlpha(placementAnimation, "index")
                    .padding(top = 10.dp * style.spacingScale, bottom = (if (categoriesWithMetadata(model, style)) 3.dp else 10.dp) * style.spacingScale)
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
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                style = storyRowTextStyle,
            )
            StoryTextColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = titleStartPadding,
                        top = (if (hasSmallPreview) 7.dp else 10.dp) * style.spacingScale,
                        end = 4.dp,
                        bottom = (if (categoriesWithMetadata(model, style)) 3.dp else if (hasSmallPreview) 7.dp else 10.dp) * style.spacingScale,
                    )
                    .alignBy(FirstBaseline),
            ) {
                // Capture the complete title group so its category chips travel with the preview.
                Column(Modifier.captureStoryPreviewElement(
                    enabled = capturePreviewSource,
                    onPositioned = { itemGeometry.titleCoordinates = it },
                    onLayerChanged = { itemGeometry.titleLayer = it },
                )) {
                    StoryTitleText(
                        text = model.title,
                        modifier = Modifier.animateStoryRowPlacement(placementAnimation)
                            .animateStoryRowTextSize(placementAnimation, titleSize),
                        badge = model.titleBadge,
                        color = foreground,
                        fontFamily = typography.family,
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize.sp,
                        style = storyRowTextStyle,
                    )
                    if (!categoriesWithMetadata(model, style)) StoryCategoryLabels(model.categoryLabels, storyCategoryAlpha(dimAlpha), tintedSurface, animate = animateContentChanges,
                        inspection = model.categoryInspection.takeIf { inspectCategories }, categoryIds = model.categoryIds,
                        placementAnimation = placementAnimation, fontSize = storyCategoryFontSize(style.textSize))
                }
                StoryVisibility(
                    visible = style.showPreviewText && model.previewText.isNotBlank(),
                    animate = animateContentChanges,
                    enter = fadeIn(contentTween()) + expandVertically(contentTween()),
                    exit = fadeOut(contentTween()) + shrinkVertically(contentTween()),
                ) {
                    Text(
                        text = model.previewText,
                        modifier = Modifier
                            .animateStoryRowPlacement(placementAnimation)
                            .animateStoryRowTextSize(placementAnimation, summarySize)
                            .storySettingsAlpha(placementAnimation, "summary")
                            .padding(top = 3.dp)
                            .captureStoryPreviewElement(
                                enabled = capturePreviewSource,
                                onPositioned = { itemGeometry.summaryCoordinates = it },
                                onLayerChanged = { itemGeometry.summaryLayer = it },
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dimAlpha),
                        fontFamily = typography.family,
                        fontSize = summarySize.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        style = storyRowTextStyle,
                    )
                }
                if (!categoriesWithMetadata(model, style)) StoryVisibility(
                    visible = !style.compact,
                    animate = animateChanges,
                    enter = fadeIn(contentTween()) + expandVertically(contentTween()),
                    exit = fadeOut(contentTween()) + shrinkVertically(contentTween()),
                ) {
                    StoryRowMovingContent(placementAnimation, "metadata") { StoryMeta(
                        placementAnimation = placementAnimation,
                        model = model,
                        style = style,
                        typography = typography,
                        dimAlpha = dimAlpha,
                        tintBaseColorArgb = tintBaseColorArgb,
                        paletteTintConfigKey = paletteTintConfigKey,
                        extractTint = extractFaviconTint,
                        onTintExtracted = onFaviconTintExtracted,
                        animateChanges = animateChanges,
                        wrapContent = placementAnimation != null,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .captureStoryPreviewElement(
                                enabled = capturePreviewSource,
                                onPositioned = { itemGeometry.metaCoordinates = it },
                                onLayerChanged = { itemGeometry.metaLayer = it },
                            ),
                    ) }
                }
                if (!categoriesWithMetadata(model, style)) StoryAuthorTag(model, typography, tintedSurface,
                    Modifier.captureStoryPreviewElement(capturePreviewSource,
                        onPositioned = { itemGeometry.authorCoordinates = it },
                        onLayerChanged = { itemGeometry.authorLayer = it })
                        .graphicsLayer { alpha = dimAlpha })
            }
            if (animateContentChanges) {
                val separateSmallPreview = placementAnimation != null && categoriesWithMetadata(model, style) &&
                    style.previewImageMode == StoryPreviewMode.SMALL
                // Moving the thumbnail to the metadata layout must not retain a second,
                // shrinking thumbnail here and temporarily squeeze the title between them.
                AnimatedVisibility(
                    visible = hasSmallPreview || separateSmallPreview,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    enter = fadeIn(contentTween()) + expandHorizontally(contentTween()),
                    exit = fadeOut(contentTween()) + shrinkHorizontally(contentTween()),
                ) {
                    if (!separateSmallPreview) StorySmallPreviewImage(
                        placementAnimation = placementAnimation,
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
                    placementAnimation = placementAnimation,
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

internal fun categoriesWithMetadata(model: StoryRowModel, style: StoryRowStyle): Boolean =
    style.categoryTagLocation == com.simon.harmonichackernews.settings.CategoryTagLocation.METADATA && model.categoryLabels.isNotEmpty()

@Composable
private fun StoryAuthorTag(model: StoryRowModel, typography: ContentTypography, tintedSurface: (() -> Color)?, modifier: Modifier = Modifier) =
    StoryAuthorTag(model.authorName, model.authorTag, model.authorTagColor, typography, tintedSurface, modifier)

@Composable
internal fun StoryAuthorTag(
    authorName: String,
    tag: String,
    tagColor: com.simon.harmonichackernews.settings.UserTagColor,
    typography: ContentTypography,
    tintedSurface: (() -> Color)?,
    modifier: Modifier = Modifier,
) {
    if (authorName.isBlank() || tag.isBlank()) return
    val base = userTagColor(tagColor)
    val accent = tintedSurface?.let { com.simon.harmonichackernews.ui.theme.contrastingColor(base, listOf(it()), 4.5f) } ?: base
    Text(annotatedUserTagLabel("$authorName ($tag)", tag, accent),
        modifier = modifier.padding(top = 2.dp).testTag("story-author-tag"),
        color = MaterialTheme.colorScheme.onSurface, fontFamily = typography.storyMetaFamily,
        fontSize = typography.storyMetaSize.sp, style = storyRowTextStyle)
}
