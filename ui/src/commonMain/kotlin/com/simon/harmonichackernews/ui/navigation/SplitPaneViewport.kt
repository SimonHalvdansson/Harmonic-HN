package com.simon.harmonichackernews.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.settings.SplitOrientation
import com.simon.harmonichackernews.settings.SplitRatioPreferences
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import kotlin.math.roundToInt

internal data class SplitPaneLayout(
    val supportsTwoPane: Boolean = false,
    val ratio: Float = 0.5f,
    val isFoldable: Boolean = false,
    val orientation: SplitOrientation = SplitOrientation.Portrait,
)
internal val LocalSplitPaneLayout = compositionLocalOf { SplitPaneLayout() }
internal val LocalSplitPaneAnimationEnabled = compositionLocalOf { true }

/** Shares persisted proportions across all Navigation3 scenes without replacing their content. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun SplitPaneViewport(
    directive: PaneScaffoldDirective,
    defaultRatio: Float,
    modifier: Modifier = Modifier,
    supportsTwoPane: Boolean = directive.maxHorizontalPartitions > 1,
    isFoldable: Boolean = false,
    content: @Composable (PaneExpansionState) -> Unit,
) {
    val repository = LocalHarmonicUiDependencies.current.settings
    val initialSettings = remember(repository) { repository.snapshot() }
    val settings by repository.updates.collectAsState(initial = initialSettings)
    val windowSize = LocalWindowInfo.current.containerSize
    val orientation = SplitOrientation.forWindow(windowSize.width, windowSize.height)
    val savedRatio = settings.appearance.splitRatio(orientation) ?: defaultRatio
    // Keep raw movement separate from the snapped value, otherwise small deltas get stuck at 50%.
    var rawRatio by remember(orientation) { mutableFloatStateOf(savedRatio) }
    var dragging by remember(orientation) { mutableStateOf(false) }
    LaunchedEffect(savedRatio, orientation) { rawRatio = savedRatio }
    val ratio = SplitRatioPreferences.snapToCenter(rawRatio, isFoldable)
    // Ratios are fractions: the default 0.01 threshold would end a crease animation
    // while it was still visibly several pixels away from the target.
    val animatedRatio = remember(orientation) { Animatable(ratio, visibilityThreshold = 0.0001f) }
    val creaseVelocity = remember(orientation) { floatArrayOf(0f) }
    val expansion = rememberPaneExpansionState()
    val animateChanges = LocalSplitPaneAnimationEnabled.current
    // Initialize before layout. Animation frames update the scaffold directly rather than
    // recomposing the navigation tree; the handle reads the same value during placement.
    SideEffect { expansion.setFirstPaneProportion(animatedRatio.value) }
    val centered = isFoldable && ratio == 0.5f
    var previouslyCentered by remember(orientation) { mutableStateOf(centered) }
    var previousRatio by remember(orientation) { mutableFloatStateOf(ratio) }
    var settlingAtCrease by remember(orientation) { mutableStateOf(false) }
    LaunchedEffect(ratio, dragging, animatedRatio, animateChanges) {
        val crossedCreaseBoundary = centered != previouslyCentered
        if (dragging && crossedCreaseBoundary) settlingAtCrease = true
        val fingerMovement = ratio - previousRatio
        previousRatio = ratio
        previouslyCentered = centered
        if (dragging && settlingAtCrease && animateChanges) {
            // Outside the magnet, carry new finger movement through immediately. Only the
            // remaining crease correction eases away, so a moving finger cannot prolong the snap.
            if (!centered && !crossedCreaseBoundary) {
                animatedRatio.snapTo(
                    (animatedRatio.value + fingerMovement).coerceIn(SplitRatioPreferences.Range),
                )
            }
            animatedRatio.animateTo(
                ratio,
                spring(stiffness = 2500f, visibilityThreshold = 0.0001f),
                initialVelocity = creaseVelocity[0],
            ) {
                creaseVelocity[0] = velocity
                expansion.setFirstPaneProportion(value)
            }
            settlingAtCrease = false
            creaseVelocity[0] = 0f
        } else if (dragging || !animateChanges) {
            creaseVelocity[0] = 0f
            animatedRatio.snapTo(ratio)
            expansion.setFirstPaneProportion(ratio)
        } else {
            creaseVelocity[0] = 0f
            settlingAtCrease = false
            // Let the preference update and hidden panes finish their one-time layout first.
            withFrameNanos { }
            animatedRatio.animateTo(ratio, tween(300, easing = FastOutSlowInEasing)) {
                expansion.setFirstPaneProportion(value)
            }
        }
    }
    val twoPane = directive.maxHorizontalPartitions > 1
    var width by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val gap = with(density) { directive.horizontalPartitionSpacerSize.toPx() }
    val targetWidth = with(density) { 48.dp.roundToPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    fun updateRatio(value: Float, persist: Boolean) {
        rawRatio = SplitRatioPreferences.sanitize(value) ?: rawRatio
        if (persist) {
            rawRatio = SplitRatioPreferences.snapToCenter(rawRatio, isFoldable)
            repository.setSplitRatio(orientation, rawRatio)
        }
    }

    CompositionLocalProvider(
        LocalSplitPaneLayout provides SplitPaneLayout(supportsTwoPane, ratio, isFoldable, orientation),
    ) {
        Box(modifier.fillMaxSize().onSizeChanged { width = it.width }) {
            content(expansion)
            AnimatedVisibility(
                visible = twoPane && settings.appearance.allowSplitAdjustment,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        IntOffset(
                            splitHandleOffset(width, gap, animatedRatio.value, targetWidth),
                            0,
                        )
                    },
            ) {
                key(orientation) {
                    var pressed by remember { mutableStateOf(false) }
                    val active = pressed || dragging
                    val handleWidth by animateDpAsState(
                        if (active) 8.dp else 4.dp,
                        tween(150, easing = FastOutSlowInEasing),
                    )
                    val handleHeight by animateDpAsState(
                        if (active) 42.dp else 32.dp,
                        tween(150, easing = FastOutSlowInEasing),
                    )
                    val handleColor by animateColorAsState(
                        if (active) HarmonicTheme.colors.accent else HarmonicTheme.colors.textSecondary,
                        tween(150, easing = FastOutSlowInEasing),
                    )
                    // The visual grip is small; its 48dp touch target overlaps both sides of the gap.
                    Box(
                        modifier = Modifier
                            .size(48.dp, 64.dp)
                            .semantics {
                                contentDescription = "Adjust split ratio"
                                stateDescription = "${(ratio * 100).roundToInt()}% list, ${(100 - ratio * 100).roundToInt()}% detail"
                                progressBarRangeInfo = ProgressBarRangeInfo(ratio, SplitRatioPreferences.Range)
                                setProgress { updateRatio(it, persist = true); true }
                            }
                            .onKeyEvent {
                                if (it.type != KeyEventType.KeyDown) return@onKeyEvent false
                                val direction = when (it.key) {
                                    Key.DirectionLeft -> -1
                                    Key.DirectionRight -> 1
                                    else -> return@onKeyEvent false
                                }
                                updateRatio(ratio + direction * (if (rtl) -0.05f else 0.05f), persist = true)
                                true
                            }
                            .focusable()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    // Observe touch-down before drag slop, without consuming the drag.
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    pressed = true
                                    try {
                                        do {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                        } while (event.changes.any { it.pressed })
                                    } finally {
                                        pressed = false
                                    }
                                }
                            }
                            .draggable(
                                orientation = Orientation.Horizontal,
                                reverseDirection = rtl,
                                state = rememberDraggableState { delta ->
                                    if (width > gap) updateRatio(rawRatio + delta / (width - gap), persist = false)
                                },
                                onDragStarted = {
                                    rawRatio = animatedRatio.value
                                    dragging = true
                                },
                                onDragStopped = {
                                    updateRatio(rawRatio, persist = true)
                                    dragging = false
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(handleWidth, handleHeight)
                                .background(handleColor, RoundedCornerShape(percent = 50)),
                        )
                    }
                }
            }
        }
    }
}

/** Proportion applies to usable pane width; the handle is centered in the intervening spacer. */
internal fun splitHandleOffset(width: Int, gap: Float, ratio: Float, targetWidth: Int): Int =
    ((width - gap).coerceAtLeast(0f) * ratio + gap / 2 - targetWidth / 2f).roundToInt()
