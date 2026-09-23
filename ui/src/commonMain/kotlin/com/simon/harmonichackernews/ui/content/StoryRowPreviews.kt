@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_arrow_drop_up
import com.simon.harmonichackernews.resources.ic_comment
import com.simon.harmonichackernews.ui.common.onSecondaryClick
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

internal val MediumPreviewImageMinimumHeight = 104.dp

@Composable
private fun Modifier.sharedStoryPreviewImage(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sharedContentKey: Any?,
): Modifier = if (
    sharedTransitionScope != null &&
    animatedVisibilityScope != null &&
    sharedContentKey != null
) {
    with(sharedTransitionScope) {
        this@sharedStoryPreviewImage.sharedElement(
            sharedContentState = rememberSharedContentState(sharedContentKey),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = BoundsTransform { _, _ -> contentTween() },
        )
    }
} else {
    this
}

private fun Modifier.renderOverSharedStoryPreviewImage(
    sharedTransitionScope: SharedTransitionScope?,
): Modifier = if (sharedTransitionScope != null) {
    with(sharedTransitionScope) {
        this@renderOverSharedStoryPreviewImage.renderInSharedTransitionScopeOverlay(
            zIndexInOverlay = 1f,
        )
    }
} else {
    this
}

@Composable
internal fun StoryMediumPreviewRail(
    model: StoryRowModel,
    style: StoryRowStyle,
    typography: ContentTypography,
    hasPreview: Boolean,
    dimAlpha: Float,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    onPreviewLoadFailed: () -> Unit,
    onPreviewLoadSuccess: () -> Unit,
    tintBaseColorArgb: Int,
    paletteTintConfigKey: String,
    extractPreviewTint: Boolean,
    onPreviewTintExtracted: (Int) -> Unit,
    capturePreviewSource: Boolean,
    itemGeometry: StoryRowGeometry,
    animateChanges: Boolean,
    sourceAccessoryAlpha: Float,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sharedContentKey: Any?,
    modifier: Modifier = Modifier,
) {
    val hazeState = if (hasPreview) rememberHazeState() else null
    val showPoints = style.showPoints && !style.compact
    val showCommentPill = style.showCommentCount && !style.compact
    val showCommentText = showCommentPill
    val borderlessImage = hasPreview && style.borderlessLargeImage
    val imageVerticalInset = if (animateChanges) {
        val animatedInset by animateDpAsState(
            targetValue = if (borderlessImage) 0.dp else 8.dp,
            animationSpec = contentTween(),
            label = "medium story image vertical inset",
        )
        animatedInset
    } else if (borderlessImage) {
        0.dp
    } else {
        8.dp
    }
    val imageEndInset = if (animateChanges) {
        val animatedInset by animateDpAsState(
            targetValue = if (borderlessImage) 0.dp else 8.dp,
            animationSpec = contentTween(),
            label = "medium story image end inset",
        )
        animatedInset
    } else if (borderlessImage) {
        0.dp
    } else {
        8.dp
    }
    val imageHeight = if (animateChanges) {
        val animatedHeight by animateDpAsState(
            targetValue = if (borderlessImage) MediumPreviewImageMinimumHeight else style.mediumPreviewImageHeight,
            animationSpec = contentTween(),
            label = "medium story image height",
        )
        animatedHeight
    } else if (borderlessImage) {
        MediumPreviewImageMinimumHeight
    } else {
        style.mediumPreviewImageHeight
    }
    val imageStartRadius = if (animateChanges) {
        val animatedRadius by animateDpAsState(
            targetValue = if (borderlessImage) 0.dp else 10.dp,
            animationSpec = contentTween(),
            label = "medium story image start radius",
        )
        animatedRadius
    } else if (borderlessImage) {
        0.dp
    } else {
        10.dp
    }
    val imageEndRadius = if (animateChanges) {
        val animatedRadius by animateDpAsState(
            targetValue = if (borderlessImage) 8.dp else 10.dp,
            animationSpec = contentTween(),
            label = "medium story image end radius",
        )
        animatedRadius
    } else if (borderlessImage) {
        8.dp
    } else {
        10.dp
    }
    val railPadding = when {
        hasPreview -> PaddingValues(
            start = 4.dp,
            top = imageVerticalInset,
            end = imageEndInset,
            bottom = imageVerticalInset,
        )
        showPoints || showCommentPill ->
            PaddingValues(start = 4.dp, top = 4.dp, end = 12.dp, bottom = 4.dp)
        else -> PaddingValues(0.dp)
    }
    val pointsDescription = "${model.points} ${if (model.points == 1) "point" else "points"}"
    val commentsDescription =
        "${model.commentCount} ${if (model.commentCount == 1) "comment" else "comments"}"

    Box(
        modifier = modifier
            .then(if (hasPreview) Modifier.fillMaxSize() else Modifier.fillMaxHeight())
            .then(
                if (!hasPreview) Modifier.combinedClickable(
                    enabled = onClick != null,
                    onClickLabel = "Open comments",
                    onLongClick = onLongClick,
                    onClick = { onClick?.invoke() },
                ).onSecondaryClick { onLongClick?.invoke() } else Modifier,
            )
            .padding(railPadding),
        contentAlignment = if (hasPreview) Alignment.Center else Alignment.CenterEnd,
    ) {
        if (hasPreview && hazeState != null) {
            Box(
                modifier = Modifier
                    .testTag("story-medium-preview-image")
                    .fillMaxWidth()
                    .height(imageHeight)
                    .clip(
                        RoundedCornerShape(
                            topStart = imageStartRadius,
                            topEnd = imageEndRadius,
                            bottomEnd = imageEndRadius,
                            bottomStart = imageStartRadius,
                        ),
                    )
                    .combinedClickable(
                        enabled = onClick != null,
                        onClickLabel = "Open comments",
                        onLongClick = onLongClick,
                        onClick = { onClick?.invoke() },
                    ).onSecondaryClick { onLongClick?.invoke() },
            ) {
                StoryPreviewImage(
                    model = model,
                    modifier = Modifier
                        .sharedStoryPreviewImage(
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            sharedContentKey = sharedContentKey,
                        )
                        .fillMaxSize()
                        .clip(
                            RoundedCornerShape(
                                topStart = imageStartRadius,
                                topEnd = imageEndRadius,
                                bottomEnd = imageEndRadius,
                                bottomStart = imageStartRadius,
                            ),
                        )
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
                        .padding(4.dp)
                        // Keep the outgoing pills intact while the image rail narrows.
                        // Their short fade completes before the image reaches small size.
                        .wrapContentWidth(Alignment.End, unbounded = true)
                        .renderOverSharedStoryPreviewImage(sharedTransitionScope)
                        .captureStoryPreviewElement(
                            enabled = capturePreviewSource,
                            onPositioned = { itemGeometry.commentsCoordinates = it },
                            onLayerChanged = { itemGeometry.commentsLayer = it },
                        )
                        .graphicsLayer(alpha = sourceAccessoryAlpha),
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
                modifier = Modifier
                    .captureStoryPreviewElement(
                        enabled = capturePreviewSource,
                        onPositioned = { itemGeometry.commentsCoordinates = it },
                        onLayerChanged = { itemGeometry.commentsLayer = it },
                    )
                    .graphicsLayer(alpha = sourceAccessoryAlpha),
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
internal fun StorySmallPreviewImage(
    model: StoryRowModel,
    dimAlpha: Float,
    onPreviewLoadFailed: () -> Unit,
    onPreviewLoadSuccess: () -> Unit,
    tintBaseColorArgb: Int,
    paletteTintConfigKey: String,
    extractPreviewTint: Boolean,
    onPreviewTintExtracted: (Int) -> Unit,
    capturePreviewSource: Boolean,
    itemGeometry: StoryRowGeometry,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sharedContentKey: Any?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(start = 4.dp, top = 6.dp, bottom = 6.dp)
            .size(width = 72.dp, height = 52.dp),
    ) {
        StoryPreviewImage(
            model = model,
            modifier = Modifier
                .sharedStoryPreviewImage(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    sharedContentKey = sharedContentKey,
                )
                .fillMaxSize()
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
