package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.theme.HarmonicTheme

private const val ContentAnimationDuration = 220
internal val ContentMotionEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

internal const val DimmedStoryAlpha = 0.6f

/** Local rendering state; resolved palettes are still published to the feature's owner. */
internal class StoryItemPresentation(
    val dimAlpha: Float,
    val cardProgress: Float,
    val outlineAlpha: Float,
    val renderedStyle: StoryItemStyle,
    val mediumAccessoryAlpha: State<Float>,
    val hasPreview: Boolean,
    val background: Color,
    val tintBaseColorArgb: Int,
    val onPreviewLoadFailed: () -> Unit,
    val onPreviewLoadSuccess: () -> Unit,
    val onPreviewTintExtracted: (Int) -> Unit,
    val onFaviconTintExtracted: (Int) -> Unit,
)

@Composable
internal fun rememberStoryItemPresentation(
    model: StoryItemUiModel,
    style: StoryItemStyle,
    listItem: Boolean,
    animate: Boolean,
    pageBackground: Color,
    onPreviewLoadSuccess: (() -> Unit)?,
    onPreviewLoadFailed: (() -> Unit)?,
    onPreviewTintExtracted: ((Int) -> Unit)?,
    onFaviconTintExtracted: ((Int) -> Unit)?,
): StoryItemPresentation {
    val colors = HarmonicTheme.colors
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
    val cardProgress = if (listItem && !animate) {
        if (style.cardStyle) 1f else 0f
    } else {
        val animatedCardProgress by animateFloatAsState(
            targetValue = if (style.cardStyle) 1f else 0f,
            animationSpec = contentTween(),
            label = "story card style",
        )
        animatedCardProgress
    }
    val outlineAlpha = if (listItem && !animate) {
        if (style.showOutline) 1f else 0f
    } else {
        val animatedOutlineAlpha by animateFloatAsState(
            targetValue = if (style.showOutline) 1f else 0f,
            animationSpec = contentTween(),
            label = "story outline",
        )
        animatedOutlineAlpha
    }
    var previewFailed by remember(model.previewImageUrl, model.previewImageLoadFailed) {
        mutableStateOf(model.previewImageLoadFailed)
    }
    val hasPreview = !previewFailed &&
        (model.previewImageUrl != null || model.previewImageFallback != null || model.previewImageBitmap != null)
    var animatedPreviewMode by remember { mutableStateOf(style.previewImageMode) }
    val mediumAccessoryFade = remember {
        Animatable(if (style.previewImageMode == StoryPreviewMode.MEDIUM) 1f else 0f)
    }
    val animatePreviewMode = animate && hasPreview && !listItem
    val renderedPreviewMode = if (animatePreviewMode) animatedPreviewMode else style.previewImageMode
    val renderedStyle = if (renderedPreviewMode == style.previewImageMode) {
        style
    } else {
        style.copy(previewImageMode = renderedPreviewMode)
    }
    LaunchedEffect(style.previewImageMode, animatePreviewMode) {
        val targetMode = style.previewImageMode
        if (
            animatePreviewMode && renderedPreviewMode == StoryPreviewMode.MEDIUM &&
            targetMode == StoryPreviewMode.SMALL
        ) {
            // Start the image transition and outgoing badge fade together.
            animatedPreviewMode = targetMode
            mediumAccessoryFade.animateTo(0f, tween(75, easing = ContentMotionEasing))
        } else {
            animatedPreviewMode = targetMode
            val targetAlpha = if (targetMode == StoryPreviewMode.MEDIUM) 1f else 0f
            if (animatePreviewMode) {
                mediumAccessoryFade.animateTo(
                    targetAlpha,
                    tween(
                        durationMillis = 75,
                        delayMillis = if (targetMode == StoryPreviewMode.MEDIUM) 105 else 0,
                        easing = ContentMotionEasing,
                    ),
                )
            } else {
                mediumAccessoryFade.snapTo(targetAlpha)
            }
        }
    }
    val tintFallback = model.tintFallbackArgb?.let(::Color) ?: colors.contentCardBackground
    val tintBaseColorArgb = tintFallback.toArgb()
    var extractedPreviewTint by remember(
        model.previewImageUrl,
        model.previewImageFallback,
        model.previewImageBitmap,
        tintBaseColorArgb,
        style.paletteTintConfigKey,
    ) { mutableStateOf<Int?>(null) }
    var extractedFaviconTint by remember(
        model.faviconUrl,
        model.faviconFallback,
        tintBaseColorArgb,
        style.paletteTintConfigKey,
    ) { mutableStateOf<Int?>(null) }
    val handlePreviewLoadFailed: () -> Unit = {
        previewFailed = true
        onPreviewLoadFailed?.invoke()
    }
    val handlePreviewLoadSuccess: () -> Unit = {
        onPreviewLoadSuccess?.invoke()
    }
    val handlePreviewTintExtracted: (Int) -> Unit = { tintColor ->
        extractedPreviewTint = tintColor
        onPreviewTintExtracted?.invoke(tintColor)
    }
    val handleFaviconTintExtracted: (Int) -> Unit = { tintColor ->
        extractedFaviconTint = tintColor
        onFaviconTintExtracted?.invoke(tintColor)
    }
    val previewAvailable = renderedPreviewMode != StoryPreviewMode.OFF && hasPreview
    // Discovering an image URL precedes decoding and palette extraction. Keep the favicon
    // tint during that gap, then transition directly to the preview tint when it is ready.
    val previewTint = (model.previewImageTintArgb ?: extractedPreviewTint)
        .takeIf { previewAvailable }
    val tint = (previewTint ?: model.faviconTintArgb ?: extractedFaviconTint)?.let(::Color)
    val targetBackground = when {
        style.tintCard -> tint ?: tintFallback
        style.hasBackground -> colors.contentCardBackground
        else -> pageBackground
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
    return StoryItemPresentation(
        dimAlpha = dimAlpha,
        cardProgress = cardProgress,
        outlineAlpha = outlineAlpha,
        renderedStyle = renderedStyle,
        mediumAccessoryAlpha = mediumAccessoryFade.asState(),
        hasPreview = hasPreview,
        background = background,
        tintBaseColorArgb = tintBaseColorArgb,
        onPreviewLoadFailed = handlePreviewLoadFailed,
        onPreviewLoadSuccess = handlePreviewLoadSuccess,
        onPreviewTintExtracted = handlePreviewTintExtracted,
        onFaviconTintExtracted = handleFaviconTintExtracted,
    )
}

private val MediumPreviewImageRailWidth = 132.dp

/** Keeps the metric rail synchronized with the shared preview image's size transition. */
@Composable
internal fun storyPreviewRailWidth(
    renderedPreviewMode: StoryPreviewMode,
    hasPreview: Boolean,
    animate: Boolean,
    listItem: Boolean,
): Dp? {
    val targetRailWidth: Dp? = when {
        renderedPreviewMode != StoryPreviewMode.MEDIUM -> 60.dp
        hasPreview -> MediumPreviewImageRailWidth
        else -> null
    }
    val animatedRailWidth = targetRailWidth?.let { width ->
        if (animate && hasPreview && !listItem) {
            val railModeTransition = updateTransition(
                targetState = renderedPreviewMode,
                label = "story preview rail mode",
            )
            val animatedWidth by railModeTransition.animateDp(
                transitionSpec = { contentTween() },
                label = "story metric rail width",
            ) { mode ->
                if (mode == StoryPreviewMode.MEDIUM) {
                    MediumPreviewImageRailWidth
                } else {
                    60.dp
                }
            }
            animatedWidth
        } else if (animate) {
            val animatedWidth by animateDpAsState(
                targetValue = width,
                animationSpec = contentTween(),
                label = "story metric rail width",
            )
            animatedWidth
        } else {
            width
        }
    }
    return animatedRailWidth
}

fun <T> contentTween() = tween<T>(
    durationMillis = ContentAnimationDuration,
    easing = ContentMotionEasing,
)
