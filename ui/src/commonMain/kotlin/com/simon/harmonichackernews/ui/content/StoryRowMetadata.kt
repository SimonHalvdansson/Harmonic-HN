@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_comment
import com.simon.harmonichackernews.resources.ic_whatshot
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.common.onSecondaryClick
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.common.sharedHazeBackground
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private const val StoryMetricPillDimStrength = 0.75f

private const val ReadNoImageMetricPillAlpha = 0.8f

private val StoryMetricPillShape = RoundedCornerShape(50)

private val StoryMetricPillHeight = 25.dp

@Composable
internal fun StoryMetricPill(
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
        colors.contentPrimary,
        colors.mutedText,
        dimAlpha,
        StoryMetricPillDimStrength,
    )
    val pillAlpha = if (hazeState == null) {
        val dimProgress = ((1f - dimAlpha) / (1f - DimmedStoryAlpha)).coerceIn(0f, 1f)
        1f + (ReadNoImageMetricPillAlpha - 1f) * dimProgress
    } else {
        1f
    }
    val backgroundModifier = if (hazeState == null) {
        Modifier
            .clip(StoryMetricPillShape)
            .background(container.copy(alpha = 0.92f))
            .border(1.dp, colors.outlineVariant, StoryMetricPillShape)
    } else {
        Modifier
            .sharedHazeBackground(
                hazeState = hazeState,
                surfaceColor = container.copy(alpha = 0.60f),
                shape = StoryMetricPillShape,
                blurRadius = 4.dp,
                glassAppearance = com.simon.harmonichackernews.ui.common.HazeGlassAppearance.FloatingButton,
            )
    }
    Row(
        modifier = Modifier
            .graphicsLayer(alpha = pillAlpha)
            .then(backgroundModifier)
            .height(if (hazeState != null) 22.dp else StoryMetricPillHeight)
            .padding(
                start = if (hazeState != null) (startPadding - 1.dp).coerceAtLeast(0.dp) else startPadding,
                end = if (hazeState != null) endPadding - 1.dp else endPadding,
            )
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
                fontSize = if (hazeState != null) 11.sp else 12.sp,
                lineHeight = if (hazeState != null) 13.sp else 14.sp,
                maxLines = 1,
                style = storyRowTextStyle,
            )
        }
    }
}

internal fun compactStoryMetric(value: Int): String {
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

internal fun readStateForeground(
    normal: Color,
    disabled: Color,
    dimAlpha: Float,
    dimStrength: Float = 1f,
): Color {
    val dimProgress = ((1f - dimAlpha) / (1f - DimmedStoryAlpha)).coerceIn(0f, 1f)
    return lerp(normal, disabled, dimProgress * dimStrength)
}

@Composable
internal fun StoryMeta(
    model: StoryRowModel,
    style: StoryRowStyle,
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
        style.previewImageMode != StoryPreviewMode.MEDIUM
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
                    color = HarmonicTheme.colors.mutedText.copy(alpha = plusVisibility),
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
                    color = HarmonicTheme.colors.mutedText.copy(alpha = pointsVisibility),
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
                    color = HarmonicTheme.colors.mutedText.copy(alpha = pointsWordVisibility),
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
                    color = HarmonicTheme.colors.mutedText.copy(alpha = pointsVisibility),
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
                    color = HarmonicTheme.colors.mutedText.copy(
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
    model: StoryRowModel,
    style: StoryRowStyle,
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
        StoryVisibility(
            visible = style.showFavicon,
            animate = animateChanges,
            enter = fadeIn(contentTween()) + expandHorizontally(
                animationSpec = contentTween(),
                expandFrom = Alignment.Start,
            ),
            exit = fadeOut(contentTween()) + shrinkHorizontally(
                animationSpec = contentTween(),
                shrinkTowards = Alignment.Start,
            ),
        ) {
            StoryFavicon(
                model = model,
                dimAlpha = dimAlpha,
                tintBaseColorArgb = tintBaseColorArgb,
                paletteTintConfigKey = paletteTintConfigKey,
                extractTint = extractTint,
                onTintExtracted = onTintExtracted,
            )
        }
        Text(
            text = metaText,
            modifier = Modifier.weight(1f),
            color = HarmonicTheme.colors.mutedText,
            fontFamily = typography.storyMetaFamily,
            fontSize = metaSize.sp,
            style = storyRowTextStyle,
        )
    }
}

@Composable
internal fun StoryCommentRail(
    model: StoryRowModel,
    style: StoryRowStyle,
    typography: ContentTypography,
    dimAlpha: Float,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    animateChanges: Boolean,
    modifier: Modifier = Modifier,
) {
    val foreground = readStateForeground(
        HarmonicTheme.colors.contentPrimary,
        HarmonicTheme.colors.mutedText,
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
            .combinedClickable(
                enabled = onClick != null,
                onClickLabel = "Open comments",
                onLongClick = onLongClick,
                onClick = { onClick?.invoke() },
            ).onSecondaryClick { onLongClick?.invoke() }
            .semantics(mergeDescendants = true) {
                contentDescription = "Open comments, ${model.commentCount} comments"
            }
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
            tint = HarmonicTheme.colors.iconTint.copy(alpha = dimAlpha),
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
                style = storyRowTextStyle,
            )
        }
    }
}
