@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.simon.harmonichackernews.ui.stories

import com.simon.harmonichackernews.resources.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val TransformDurationMillis = 280
private const val PredictiveBackTranslationXDp = 56f
private const val PredictiveBackTranslationYDp = 18f
private val StoryPreviewShape = RoundedCornerShape(28.dp)
private val StoryPreviewShadowElevation = 8.dp

/** Shared pager, list synchronization, container transform, and predictive-back presentation. */
@Composable
fun SharedStoryPreviewOverlay(
    controller: StoriesComposeController,
    tablet: Boolean,
    cardContent: @Composable (
        story: com.simon.harmonichackernews.presentation.StoryListItemSnapshot,
        page: Int,
        cardColor: Color,
        modifier: Modifier,
    ) -> Unit,
) {
    val state = controller.storyPreviewOverlay ?: return
    val density = LocalDensity.current
    val pagerState = rememberPagerState(
        initialPage = state.initialPage,
        pageCount = { state.stories.size },
    )
    val transformProgress = remember(state) { Animatable(0f) }
    val predictiveProgressAnimation = remember(state) { Animatable(0f) }
    var targetBounds by remember(state) { mutableStateOf<Rect?>(null) }
    var lastPagerPosition by remember(state) { mutableFloatStateOf(state.initialPage.toFloat()) }
    var pendingListScroll by remember(state) { mutableFloatStateOf(0f) }
    val dismissRequest = controller.storyPreviewDismissRequest
    val predictiveSettleRequest = controller.storyPreviewPredictiveBackSettleRequest

    LaunchedEffect(state, targetBounds) {
        if (targetBounds != null && dismissRequest == 0) {
            transformProgress.animateTo(
                1f,
                tween(TransformDurationMillis, easing = FastOutSlowInEasing),
            )
        }
    }
    LaunchedEffect(dismissRequest) {
        if (dismissRequest == 0) return@LaunchedEffect
        transformProgress.animateTo(
            0f,
            tween(TransformDurationMillis, easing = FastOutSlowInEasing),
        )
        controller.completeStoryPreviewDismiss()
    }
    LaunchedEffect(controller.storyPreviewPredictiveBackProgress, predictiveSettleRequest) {
        if (predictiveSettleRequest == null) {
            predictiveProgressAnimation.snapTo(
                controller.storyPreviewPredictiveBackProgress.coerceIn(0f, 1f),
            )
        }
    }
    LaunchedEffect(predictiveSettleRequest?.serial) {
        val request = predictiveSettleRequest ?: return@LaunchedEffect
        predictiveProgressAnimation.animateTo(
            request.target,
            tween(180, easing = FastOutSlowInEasing),
        )
        controller.finishStoryPreviewPredictiveBackSettle(request)
    }
    LaunchedEffect(pagerState, state) {
        snapshotFlow {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, (state.stories.size - 1).coerceAtLeast(0).toFloat())
        }.collect { position ->
            val lower = floor(position).toInt().coerceIn(state.stories.indices)
            val upper = ceil(position).toInt().coerceIn(state.stories.indices)
            val offset = position - lower
            controller.onStoryPreviewPagePosition(lower, upper, offset)

            val delta = position - lastPagerPosition
            if (delta != 0f && state.stories.size > 1) {
                var cursor = lastPagerPosition
                if (position > lastPagerPosition) {
                    while (cursor < position) {
                        val segment = floor(cursor).toInt()
                            .coerceAtLeast(0)
                            .coerceAtMost(state.stories.lastIndex - 1)
                        val end = min(position, segment + 1f)
                        val first = state.stories[segment]
                        pendingListScroll += (end - cursor) *
                            controller.getAdjacentStoryPagingDistance(first.id)
                        cursor = end
                    }
                } else {
                    while (cursor > position) {
                        val segment = (ceil(cursor).toInt() - 1)
                            .coerceAtLeast(0)
                            .coerceAtMost(state.stories.lastIndex - 1)
                        val end = max(position, segment.toFloat())
                        val first = state.stories[segment]
                        pendingListScroll += (end - cursor) *
                            controller.getAdjacentStoryPagingDistance(first.id)
                        cursor = end
                    }
                }
                val wholePixels = if (pendingListScroll > 0f) {
                    floor(pendingListScroll).toInt()
                } else {
                    ceil(pendingListScroll).toInt()
                }
                if (wholePixels != 0) {
                    controller.requestScrollBy(wholePixels)
                    pendingListScroll -= wholePixels
                }
            }
            lastPagerPosition = position
        }
    }
    LaunchedEffect(pagerState, state) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect(controller::onStoryPreviewPageSettled)
    }

    val progress = transformProgress.value
    val predictiveProgress = predictiveProgressAnimation.value
    val predictiveEased = 1f - (1f - predictiveProgress) * (1f - predictiveProgress)
    val backDirection = if (controller.storyPreviewPredictiveBackEdge == 1) -1f else 1f
    val backTranslationX = with(density) { PredictiveBackTranslationXDp.dp.toPx() } *
        predictiveEased * backDirection
    val backTranslationY = with(density) { PredictiveBackTranslationYDp.dp.toPx() } *
        predictiveEased
    val currentStory = state.stories[pagerState.currentPage]
    val sourceBounds = controller.sourceBoundsForStory(currentStory.id)
    val target = targetBounds
    val startScaleX = if (sourceBounds != null && target != null && target.width > 0f) {
        (sourceBounds.width / target.width).coerceIn(0.08f, 1.15f)
    } else 0.96f
    val startScaleY = if (sourceBounds != null && target != null && target.height > 0f) {
        (sourceBounds.height / target.height).coerceIn(0.08f, 1.15f)
    } else 0.96f
    val startTranslationX = if (sourceBounds != null && target != null) {
        sourceBounds.center.x - target.center.x
    } else 0f
    val startTranslationY = if (sourceBounds != null && target != null) {
        sourceBounds.center.y - target.center.y
    } else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color.Black.copy(
                    alpha = 0.32f * progress * (1f - 0.55f * predictiveEased),
                ),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = controller::requestDismissStoryPreview,
            ),
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 2,
            userScrollEnabled = progress >= 0.999f && dismissRequest == 0,
            key = { page -> "${state.stories[page].id}:$page" },
        ) { page ->
            val pageOffset = abs(
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction,
            )
            val pageAlpha = (1f - ((pageOffset - 0.75f) / 0.25f)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(
                        horizontal = HarmonicDimens.compose_comment_action_screen_padding_horizontal,
                        vertical = HarmonicDimens.compose_comment_action_screen_padding_vertical,
                    )
                    .graphicsLayer(alpha = pageAlpha),
                contentAlignment = Alignment.Center,
            ) {
                cardContent(
                    state.stories[page],
                    page,
                    Color(state.cardColors[page]),
                    Modifier
                        .widthIn(
                            max = if (tablet) {
                                HarmonicDimens.compose_comment_action_tablet_max_width
                            } else {
                                HarmonicDimens.compose_comment_action_max_width
                            },
                        )
                        .fillMaxWidth()
                        .then(
                            if (page == pagerState.currentPage) {
                                Modifier.onGloballyPositioned { targetBounds = it.boundsInWindow() }
                            } else {
                                Modifier
                            },
                        )
                        .graphicsLayer {
                            shadowElevation = StoryPreviewShadowElevation.toPx()
                            shape = StoryPreviewShape
                            clip = false
                            if (page == pagerState.currentPage) {
                                val sharedScaleX = startScaleX + (1f - startScaleX) * progress
                                val sharedScaleY = startScaleY + (1f - startScaleY) * progress
                                val backScale = 1f - 0.1f * predictiveEased
                                scaleX = sharedScaleX * backScale
                                scaleY = sharedScaleY * backScale
                                translationX = startTranslationX * (1f - progress) + backTranslationX
                                translationY = startTranslationY * (1f - progress) + backTranslationY
                                alpha = if (sourceBounds == null) progress else max(0.7f, progress)
                                transformOrigin = TransformOrigin(
                                    if (backDirection > 0f) 0f else 1f,
                                    0.5f,
                                )
                            }
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                )
            }
        }
    }
}
