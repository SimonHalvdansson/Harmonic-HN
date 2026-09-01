@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

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
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.common.GraphicsLayerSnapshot
import com.simon.harmonichackernews.ui.common.rememberGraphicsLayerSnapshot
import com.simon.harmonichackernews.ui.common.shouldUpdateRestingTargetGeometry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val TransformDurationMillis = 280
private const val PredictiveBackTranslationXDp = 56f
private const val PredictiveBackTranslationYDp = 18f
private const val PagerSettledOffsetTolerance = 0.001f
private const val DismissFallbackDelayMillis = 460L
private const val ScrollWheelGestureIdleMillis = 100L
private const val PreviewPagerSnapPositionalThreshold = 0.18f

internal enum class StoryPreviewOpeningDecision {
    Animate,
    SnapToOpen,
}

internal fun storyPreviewOpeningDecision(
    current: StoryPreviewOpeningDecision?,
    snapshotsReady: Boolean,
    snapshotsUnavailable: Boolean,
    hasTargetBounds: Boolean,
    dismissRequested: Boolean,
): StoryPreviewOpeningDecision? = when {
    current != null -> current
    dismissRequested || !hasTargetBounds -> null
    snapshotsUnavailable -> StoryPreviewOpeningDecision.SnapToOpen
    snapshotsReady -> StoryPreviewOpeningDecision.Animate
    else -> null
}

internal fun storyPreviewOptionalSnapshotUnavailable(
    layerHasContent: Boolean,
    snapshotUnavailable: Boolean,
): Boolean = layerHasContent && snapshotUnavailable

internal fun storyPreviewPagerSettleTarget(
    isScrollInProgress: Boolean,
    currentPage: Int,
    currentPageOffsetFraction: Float,
): Int? = currentPage.takeIf {
    !isScrollInProgress &&
        currentPageOffsetFraction.isFinite() &&
        abs(currentPageOffsetFraction) > PagerSettledOffsetTolerance
}

internal fun storyPreviewScrollWheelTarget(
    currentPage: Int,
    pageCount: Int,
    scrollDeltaY: Float,
): Int? {
    if (pageCount <= 0 || currentPage !in 0..<pageCount) return null
    if (!scrollDeltaY.isFinite() || scrollDeltaY == 0f) return null
    val target = currentPage + if (scrollDeltaY > 0f) 1 else -1
    return target.coerceIn(0, pageCount - 1).takeIf { it != currentPage }
}

/** Shared pager, list synchronization, container transform, and predictive-back presentation. */
@Composable
fun StoryPreviewOverlay(
    controller: StoriesComposeController,
    tablet: Boolean,
    pageOnScrollWheel: Boolean = false,
    onScrimAlphaChanged: (Float) -> Unit = {},
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
    val pagerFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        // Keep the pager's own velocity/settling strategy as the single source of truth. A quarter
        // page is enough for a deliberate drag, while shorter releases return immediately unless
        // the built-in fling velocity commits them.
        snapPositionalThreshold = PreviewPagerSnapPositionalThreshold,
    )
    val currentStory = state.stories[pagerState.currentPage]
    val pagerSettlingScope = rememberCoroutineScope()
    var scrollWheelGestureReady by remember(state) { mutableStateOf(true) }
    var scrollWheelResetJob by remember(state) { mutableStateOf<Job?>(null) }
    val transformProgress = remember(state) { Animatable(0f) }
    val predictiveProgressAnimation = remember(state) { Animatable(0f) }
    var overlayActive by remember(state) { mutableStateOf(false) }
    var hideTargetContent by remember(state) { mutableStateOf(true) }
    var drawOverlayShadows by remember(state) { mutableStateOf(false) }
    var openingStarted by remember(state) { mutableStateOf(false) }
    var openingCompleted by remember(state) { mutableStateOf(false) }
    var openingDecision by remember(state) {
        mutableStateOf<StoryPreviewOpeningDecision?>(null)
    }
    var closingStarted by remember(state) { mutableStateOf(false) }
    var rootOffset by remember(state) { mutableStateOf(Offset.Zero) }
    var targetBounds by remember(state, currentStory.id) { mutableStateOf<Rect?>(null) }
    var targetImageBounds by remember(state, currentStory.id) { mutableStateOf<Rect?>(null) }
    var targetTitleBounds by remember(state, currentStory.id) { mutableStateOf<Rect?>(null) }
    var targetSummaryBounds by remember(state, currentStory.id) { mutableStateOf<Rect?>(null) }
    var targetMetaBounds by remember(state, currentStory.id) { mutableStateOf<Rect?>(null) }
    var targetSupplementaryBounds by remember(state, currentStory.id) {
        mutableStateOf<Rect?>(null)
    }
    var targetCommentsButtonBounds by remember(state, currentStory.id) {
        mutableStateOf<Rect?>(null)
    }
    var targetImageLayer by remember(state, currentStory.id) {
        mutableStateOf<GraphicsLayer?>(null)
    }
    var targetTitleLayer by remember(state, currentStory.id) {
        mutableStateOf<GraphicsLayer?>(null)
    }
    var targetSummaryLayer by remember(state, currentStory.id) {
        mutableStateOf<GraphicsLayer?>(null)
    }
    var targetMetaLayer by remember(state, currentStory.id) {
        mutableStateOf<GraphicsLayer?>(null)
    }
    var targetSupplementaryLayer by remember(state, currentStory.id) {
        mutableStateOf<GraphicsLayer?>(null)
    }
    val dismissRequest = controller.storyPreviewDismissRequest
    val updateRestingTargetGeometry = shouldUpdateRestingTargetGeometry(
        predictiveBackProgress = controller.storyPreviewPredictiveBackProgress,
        dismissRequestVersion = dismissRequest,
    )
    val predictiveSettleRequest = controller.storyPreviewPredictiveBackSettleRequest
    // An interrupted opening has not exposed interactive dialog content, so its existing snapshots
    // are already current. Reusing them makes the transform reverse immediately. A fully opened
    // dialog still refreshes snapshots on dismiss to include any actions or late-loaded content.
    val snapshotRefreshKey = if (dismissRequest != 0 && !openingCompleted) 0 else dismissRequest
    val targetImageCapture = rememberGraphicsLayerSnapshot(targetImageLayer, snapshotRefreshKey)
    val targetTitleCapture = rememberGraphicsLayerSnapshot(targetTitleLayer, snapshotRefreshKey)
    val targetSummaryCapture = rememberGraphicsLayerSnapshot(targetSummaryLayer, snapshotRefreshKey)
    val targetMetaCapture = rememberGraphicsLayerSnapshot(targetMetaLayer, snapshotRefreshKey)
    val targetSupplementaryCapture =
        rememberGraphicsLayerSnapshot(targetSupplementaryLayer, snapshotRefreshKey)
    val sourceGeometry = controller.sourceGeometryForStory(currentStory.id)
    val sourceImageCapture = rememberGraphicsLayerSnapshot(sourceGeometry?.imageLayer, snapshotRefreshKey)
    val sourceTitleCapture = rememberGraphicsLayerSnapshot(sourceGeometry?.titleLayer, snapshotRefreshKey)
    val sourceSummaryCapture = rememberGraphicsLayerSnapshot(sourceGeometry?.summaryLayer, snapshotRefreshKey)
    val sourceMetaCapture = rememberGraphicsLayerSnapshot(sourceGeometry?.metaLayer, snapshotRefreshKey)
    val sourceIndexCapture = rememberGraphicsLayerSnapshot(sourceGeometry?.indexLayer, snapshotRefreshKey)
    val sourceCommentsCapture = rememberGraphicsLayerSnapshot(
        sourceGeometry?.commentsLayer,
        snapshotRefreshKey,
    )
    val targetImageSnapshot = targetImageCapture.image
    val targetTitleSnapshot = targetTitleCapture.image
    val targetSummarySnapshot = targetSummaryCapture.image
    val targetMetaSnapshot = targetMetaCapture.image
    val targetSupplementarySnapshot = targetSupplementaryCapture.image
    val sourceImageSnapshot = sourceImageCapture.image
    val sourceTitleSnapshot = sourceTitleCapture.image
    val sourceSummarySnapshot = sourceSummaryCapture.image
    val sourceMetaSnapshot = sourceMetaCapture.image
    val sourceIndexSnapshot = sourceIndexCapture.image
    val sourceCommentsSnapshot = sourceCommentsCapture.image
    var lastPagerPosition by remember(state) { mutableFloatStateOf(state.initialPage.toFloat()) }
    var pendingListScroll by remember(state) { mutableFloatStateOf(0f) }
    fun sourceSnapshotReady(bounds: Rect?, capture: GraphicsLayerSnapshot): Boolean =
        bounds == null || capture.isCurrent(snapshotRefreshKey)
    fun sourceSnapshotUnavailable(bounds: Rect?, capture: GraphicsLayerSnapshot): Boolean =
        bounds != null && capture.isUnavailable(snapshotRefreshKey)
    val sourceSnapshotsReady =
        sourceGeometry != null &&
            sourceSnapshotReady(sourceGeometry.image, sourceImageCapture) &&
            sourceSnapshotReady(sourceGeometry.title, sourceTitleCapture) &&
            sourceSnapshotReady(sourceGeometry.summary, sourceSummaryCapture) &&
            sourceSnapshotReady(sourceGeometry.meta, sourceMetaCapture) &&
            sourceSnapshotReady(sourceGeometry.index, sourceIndexCapture) &&
            sourceSnapshotReady(sourceGeometry.comments, sourceCommentsCapture)
    val targetImageLayerHasContent = targetImageLayer
        ?.takeIf { it.size.width > 0 && it.size.height > 0 } != null
    val targetSummaryLayerHasContent = targetSummaryLayer
        ?.takeIf { it.size.width > 0 && it.size.height > 0 } != null
    val snapshotsReadyForTransition =
        sourceSnapshotsReady &&
            targetBounds != null &&
            targetCommentsButtonBounds != null &&
            targetTitleBounds != null &&
            targetTitleCapture.isCurrent(snapshotRefreshKey) &&
            targetMetaCapture.isCurrent(snapshotRefreshKey) &&
            targetSupplementaryCapture.isCurrent(snapshotRefreshKey) &&
            (!targetImageLayerHasContent ||
                targetImageCapture.isCurrent(snapshotRefreshKey)) &&
            (!targetSummaryLayerHasContent ||
                targetSummaryCapture.isCurrent(snapshotRefreshKey))
    val snapshotsUnavailableForTransition = sourceGeometry == null ||
        sourceSnapshotUnavailable(sourceGeometry.image, sourceImageCapture) ||
        sourceSnapshotUnavailable(sourceGeometry.title, sourceTitleCapture) ||
        sourceSnapshotUnavailable(sourceGeometry.summary, sourceSummaryCapture) ||
        sourceSnapshotUnavailable(sourceGeometry.meta, sourceMetaCapture) ||
        sourceSnapshotUnavailable(sourceGeometry.index, sourceIndexCapture) ||
        sourceSnapshotUnavailable(sourceGeometry.comments, sourceCommentsCapture) ||
        targetTitleCapture.isUnavailable(snapshotRefreshKey) ||
        targetMetaCapture.isUnavailable(snapshotRefreshKey) ||
        targetSupplementaryCapture.isUnavailable(snapshotRefreshKey) ||
        storyPreviewOptionalSnapshotUnavailable(
            targetImageLayerHasContent,
            targetImageCapture.isUnavailable(snapshotRefreshKey),
        ) ||
        storyPreviewOptionalSnapshotUnavailable(
            targetSummaryLayerHasContent,
            targetSummaryCapture.isUnavailable(snapshotRefreshKey),
        )

    // YouTube oEmbed commonly resolves its title and thumbnail just after the dialog is composed.
    // Those updates replace capture layers and temporarily make the latest snapshots unready. Latch
    // the first valid opening decision so that transient content enrichment cannot cancel an
    // already-started container transform.
    LaunchedEffect(
        state,
        snapshotsReadyForTransition,
        snapshotsUnavailableForTransition,
        dismissRequest,
        targetBounds,
    ) {
        openingDecision = storyPreviewOpeningDecision(
            current = openingDecision,
            snapshotsReady = snapshotsReadyForTransition,
            snapshotsUnavailable = snapshotsUnavailableForTransition,
            hasTargetBounds = targetBounds != null,
            dismissRequested = dismissRequest != 0,
        )
    }
    LaunchedEffect(state, openingDecision, dismissRequest) {
        if (dismissRequest != 0 || openingStarted) return@LaunchedEffect
        when (openingDecision) {
            null -> return@LaunchedEffect
            StoryPreviewOpeningDecision.SnapToOpen -> {
                openingStarted = true
                transformProgress.snapTo(1f)
                hideTargetContent = false
                controller.setStoryPreviewSourceCovered(false)
                openingCompleted = true
                return@LaunchedEffect
            }
            StoryPreviewOpeningDecision.Animate -> Unit
        }
        openingStarted = true
        // First cover the still-live source with an identical progress-zero overlay. Only hide the
        // list row after that overlay has reached the screen, so there is no empty handoff frame.
        overlayActive = true
        withFrameNanos { }
        controller.setStoryPreviewSourceCovered(true)
        drawOverlayShadows = true
        withFrameNanos { }
        transformProgress.animateTo(
            1f,
            tween(TransformDurationMillis, easing = FastOutSlowInEasing),
        )
        // Likewise, draw the final card below the progress-one overlay for one frame before the
        // overlay is removed.
        hideTargetContent = false
        drawOverlayShadows = false
        withFrameNanos { }
        openingCompleted = true
        overlayActive = false
    }
    LaunchedEffect(
        dismissRequest,
        snapshotsReadyForTransition,
        snapshotsUnavailableForTransition,
    ) {
        if (dismissRequest == 0) return@LaunchedEffect
        if (closingStarted) return@LaunchedEffect
        if (snapshotsUnavailableForTransition) {
            closingStarted = true
            controller.setStoryPreviewSourceCovered(false)
            controller.completeStoryPreviewDismiss()
            return@LaunchedEffect
        }
        if (!snapshotsReadyForTransition) return@LaunchedEffect
        closingStarted = true
        // Put the overlay over the still-live dialog first, then hide the live dialog beneath it.
        overlayActive = true
        withFrameNanos { }
        hideTargetContent = true
        drawOverlayShadows = true
        withFrameNanos { }
        transformProgress.animateTo(
            0f,
            tween(TransformDurationMillis, easing = FastOutSlowInEasing),
        )
        // Reveal the list row underneath the identical progress-zero overlay before removing the
        // dialog host. This closes the second possible one-frame gap.
        controller.setStoryPreviewSourceCovered(false)
        drawOverlayShadows = false
        withFrameNanos { }
        controller.completeStoryPreviewDismiss()
    }
    // A navigation or resize can invalidate a transition layer after the dialog has opened. A
    // dismiss must never remain pending forever in that state: pending dismissals disable paging
    // and ignore subsequent dismiss requests, effectively trapping the user behind the overlay.
    LaunchedEffect(dismissRequest) {
        if (dismissRequest == 0) return@LaunchedEffect
        delay(DismissFallbackDelayMillis)
        if (
            controller.storyPreviewDismissRequest == dismissRequest &&
            !closingStarted
        ) {
            controller.setStoryPreviewSourceCovered(false)
            controller.completeStoryPreviewDismiss()
        }
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
    LaunchedEffect(pagerState, state) {
        snapshotFlow {
            storyPreviewPagerSettleTarget(
                isScrollInProgress = pagerState.isScrollInProgress,
                currentPage = pagerState.currentPage,
                currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
            )
        }.distinctUntilChanged().collect { targetPage ->
            if (targetPage != null) {
                // Starting another gesture can cancel Pager's return animation after an incomplete
                // swipe. Run the repair in a child job so that cancellation does not kill this
                // observer; once the pager is idle again it can retry and finish centering the page.
                pagerSettlingScope.launch {
                    pagerState.animateScrollToPage(targetPage)
                }
            }
        }
    }

    val progress = transformProgress.value
    val predictiveProgress = predictiveProgressAnimation.value
    val predictiveEased = 1f - (1f - predictiveProgress) * (1f - predictiveProgress)
    val backDirection = if (controller.storyPreviewPredictiveBackEdge == 1) -1f else 1f
    val backScale = 1f - 0.1f * predictiveEased
    val backPivotFractionX = if (backDirection > 0f) 0f else 1f
    val backTranslationX = with(density) { PredictiveBackTranslationXDp.dp.toPx() } *
        predictiveEased * backDirection
    val backTranslationY = with(density) { PredictiveBackTranslationYDp.dp.toPx() } *
        predictiveEased
    val scrimAlpha = 0.32f * progress * (1f - 0.55f * predictiveEased)
    SideEffect { onScrimAlphaChanged(scrimAlpha) }
    fun transformTargetBounds(bounds: Rect?): Rect? {
        val container = targetBounds ?: return bounds
        return bounds?.let {
            storyPreviewPredictiveBackBounds(
                bounds = it,
                container = container,
                scale = backScale,
                translationX = backTranslationX,
                translationY = backTranslationY,
                pivotFractionX = backPivotFractionX,
            )
        }
    }
    val sharedTransition = StoryPreviewSharedTransitionState(
        progress = progress,
        active = overlayActive,
        hideTargetContent = hideTargetContent,
        drawOverlayShadows = drawOverlayShadows,
        source = sourceGeometry,
        sourceSnapshot = { element ->
            when (element) {
                StoryPreviewSourceElement.Image -> sourceImageSnapshot
                StoryPreviewSourceElement.Title -> sourceTitleSnapshot
                StoryPreviewSourceElement.Summary -> sourceSummarySnapshot
                StoryPreviewSourceElement.Meta -> sourceMetaSnapshot
                StoryPreviewSourceElement.Index -> sourceIndexSnapshot
                StoryPreviewSourceElement.Comments -> sourceCommentsSnapshot
            }
        },
        // The live card is already transformed by predictive back when a committed gesture starts
        // the shared-element dismissal. Use that same geometry for the overlay's progress-one
        // frame so handing drawing from the live card to the overlay cannot jump back to rest.
        targetContainer = transformTargetBounds(targetBounds),
        targetScale = backScale,
        targetCommentsButton = transformTargetBounds(targetCommentsButtonBounds),
        rootOffset = rootOffset,
        targetBounds = { element ->
            transformTargetBounds(when (element) {
                StoryPreviewSharedElement.Image -> targetImageBounds
                StoryPreviewSharedElement.Title -> targetTitleBounds
                StoryPreviewSharedElement.Summary -> targetSummaryBounds
                StoryPreviewSharedElement.Meta -> targetMetaBounds
                StoryPreviewSharedElement.Supplementary -> targetSupplementaryBounds
            })
        },
        targetSnapshot = { element ->
            when (element) {
                StoryPreviewSharedElement.Image -> targetImageSnapshot
                StoryPreviewSharedElement.Title -> targetTitleSnapshot
                StoryPreviewSharedElement.Summary -> targetSummarySnapshot
                StoryPreviewSharedElement.Meta -> targetMetaSnapshot
                StoryPreviewSharedElement.Supplementary -> targetSupplementarySnapshot
            }
        },
        updateTargetBounds = { element, bounds ->
            if (updateRestingTargetGeometry) {
                when (element) {
                    StoryPreviewSharedElement.Image -> {
                        if (targetImageBounds != bounds) targetImageBounds = bounds
                    }
                    StoryPreviewSharedElement.Title -> {
                        if (targetTitleBounds != bounds) targetTitleBounds = bounds
                    }
                    StoryPreviewSharedElement.Summary -> {
                        if (targetSummaryBounds != bounds) targetSummaryBounds = bounds
                    }
                    StoryPreviewSharedElement.Meta -> {
                        if (targetMetaBounds != bounds) targetMetaBounds = bounds
                    }
                    StoryPreviewSharedElement.Supplementary -> {
                        if (targetSupplementaryBounds != bounds) {
                            targetSupplementaryBounds = bounds
                        }
                    }
                }
            }
        },
        updateTargetLayer = { element, layer ->
            when (element) {
                StoryPreviewSharedElement.Image -> {
                    if (targetImageLayer !== layer) targetImageLayer = layer
                }
                StoryPreviewSharedElement.Title -> {
                    if (targetTitleLayer !== layer) targetTitleLayer = layer
                }
                StoryPreviewSharedElement.Summary -> {
                    if (targetSummaryLayer !== layer) targetSummaryLayer = layer
                }
                StoryPreviewSharedElement.Meta -> {
                    if (targetMetaLayer !== layer) targetMetaLayer = layer
                }
                StoryPreviewSharedElement.Supplementary -> {
                    if (targetSupplementaryLayer !== layer) targetSupplementaryLayer = layer
                }
            }
        },
        updateCommentsButtonBounds = { bounds ->
            if (updateRestingTargetGeometry && targetCommentsButtonBounds != bounds) {
                targetCommentsButtonBounds = bounds
            }
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOffset = it.boundsInWindow().topLeft }
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = controller::requestDismissStoryPreview,
            ),
    ) {
        VerticalPager(
            state = pagerState,
            flingBehavior = pagerFlingBehavior,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (pageOnScrollWheel) {
                        Modifier.storyPreviewScrollWheelPaging { scrollDeltaY ->
                            scrollWheelResetJob?.cancel()
                            scrollWheelResetJob = pagerSettlingScope.launch {
                                delay(ScrollWheelGestureIdleMillis)
                                while (pagerState.isScrollInProgress) delay(16L)
                                scrollWheelGestureReady = true
                            }
                            if (
                                !scrollWheelGestureReady ||
                                progress < 0.999f ||
                                dismissRequest != 0
                            ) {
                                return@storyPreviewScrollWheelPaging
                            }
                            val target = storyPreviewScrollWheelTarget(
                                currentPage = pagerState.currentPage,
                                pageCount = state.stories.size,
                                scrollDeltaY = scrollDeltaY,
                            ) ?: return@storyPreviewScrollWheelPaging
                            scrollWheelGestureReady = false
                            pagerSettlingScope.launch {
                                pagerState.animateScrollToPage(target)
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            beyondViewportPageCount = 2,
            userScrollEnabled = progress >= 0.999f && dismissRequest == 0,
            key = { page -> "${state.stories[page].id}:$page" },
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(
                        horizontal = HarmonicDimens.compose_comment_action_screen_padding_horizontal,
                        vertical = HarmonicDimens.compose_comment_action_screen_padding_vertical,
                    )
                    .graphicsLayer {
                        val pageOffset = abs(
                            (pagerState.currentPage - page) +
                                pagerState.currentPageOffsetFraction,
                        )
                        alpha = (1f - ((pageOffset - 0.75f) / 0.25f)).coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.Center,
            ) {
                val currentPage = page == pagerState.currentPage
                val cardModifier = Modifier
                    .widthIn(
                        max = if (tablet) {
                            HarmonicDimens.compose_comment_action_tablet_max_width
                        } else {
                            HarmonicDimens.compose_comment_action_max_width
                        },
                    )
                    .fillMaxWidth()
                    .then(
                        if (currentPage) {
                            Modifier.onGloballyPositioned {
                                if (updateRestingTargetGeometry) {
                                    targetBounds = it.boundsInWindow()
                                }
                            }
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (currentPage && predictiveEased > 0f) {
                            Modifier.graphicsLayer {
                                scaleX = backScale
                                scaleY = backScale
                                translationX = backTranslationX
                                translationY = backTranslationY
                                transformOrigin = TransformOrigin(
                                    backPivotFractionX,
                                    0.5f,
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                if (currentPage) {
                    CompositionLocalProvider(
                        LocalStoryPreviewSharedTransition provides sharedTransition,
                    ) {
                        cardContent(
                            state.stories[page],
                            page,
                            Color(state.cardBackgrounds[page].value),
                            cardModifier,
                        )
                    }
                } else {
                    cardContent(
                        state.stories[page],
                        page,
                        Color(state.cardBackgrounds[page].value),
                        cardModifier,
                    )
                }
            }
        }
        StoryPreviewTransitionOverlay(
            transition = sharedTransition,
            color = Color(state.cardBackgrounds[pagerState.currentPage].value),
        )
    }
}

internal fun storyPreviewPredictiveBackBounds(
    bounds: Rect,
    container: Rect,
    scale: Float,
    translationX: Float,
    translationY: Float,
    pivotFractionX: Float,
): Rect {
    val pivotX = container.left + container.width * pivotFractionX
    val pivotY = container.center.y
    fun transformX(value: Float): Float =
        pivotX + (value - pivotX) * scale + translationX
    fun transformY(value: Float): Float =
        pivotY + (value - pivotY) * scale + translationY
    return Rect(
        left = transformX(bounds.left),
        top = transformY(bounds.top),
        right = transformX(bounds.right),
        bottom = transformY(bounds.bottom),
    )
}
