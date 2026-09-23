@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.stories

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import com.simon.harmonichackernews.ui.common.consumeAllPointerGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

private val StoriesRootEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

internal fun resolvedStandardSearchProgress(
    searching: Boolean,
    suppressSearchAutoFocus: Boolean,
    animatedProgress: Float,
): Float = if (!searching && suppressSearchAutoFocus) 0f else animatedProgress

internal fun shouldComposeSearchLayer(
    searching: Boolean,
    predictiveBackActive: Boolean,
    searchProgress: Float,
): Boolean = searching || predictiveBackActive || searchProgress > 0f

/** Shared root transition between a primary story feed and its search results. */
@Composable
fun StoriesRoot(
    searching: Boolean,
    suppressSearchAutoFocus: Boolean,
    predictiveBackActive: Boolean,
    predictiveBackProgress: Float,
    backgroundColor: Color,
    mainLayer: @Composable () -> Unit,
    searchLayer: @Composable () -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val animatedSearchProgress by animateFloatAsState(
        targetValue = if (searching) 1f else 0f,
        animationSpec = if (!searching && suppressSearchAutoFocus) {
            snap()
        } else {
            tween(180, easing = StoriesRootEasing)
        },
        label = "stories search transition",
    )
    val standardSearchProgress = resolvedStandardSearchProgress(
        searching = searching,
        suppressSearchAutoFocus = suppressSearchAutoFocus,
        animatedProgress = animatedSearchProgress,
    )
    val progress = predictiveBackProgress.coerceIn(0f, 1f)
    val predictiveSearchFade = (progress / 0.5f).coerceIn(0f, 1f)
    val predictiveMainFade = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
    val searchAlpha = if (predictiveBackActive) {
        1f - predictiveSearchFade
    } else {
        standardSearchProgress
    }
    val mainAlpha = if (predictiveBackActive) {
        predictiveMainFade
    } else {
        1f - standardSearchProgress
    }
    val mainActive = !predictiveBackActive && !searching
    val searchActive = !predictiveBackActive && searching

    Box(Modifier.fillMaxSize().background(backgroundColor)) {
        StableStoryLayer(
            active = mainActive,
            modifier = Modifier
                .matchParentSize()
                .zIndex(if (mainActive) 1f else 0f)
                .graphicsLayer {
                    alpha = mainAlpha
                    translationY = if (predictiveBackActive) {
                        24.dp.toPx() * (1f - predictiveMainFade)
                    } else {
                        24.dp.toPx() * standardSearchProgress
                    }
                },
            content = mainLayer,
        )
        if (shouldComposeSearchLayer(searching, predictiveBackActive, standardSearchProgress)) {
            StableStoryLayer(
                active = searchActive,
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(if (searchActive) 1f else 0f)
                    .graphicsLayer {
                        alpha = searchAlpha
                        translationY = if (predictiveBackActive) {
                            24.dp.toPx() * predictiveSearchFade
                        } else {
                            24.dp.toPx() * (1f - standardSearchProgress)
                        }
                    },
                content = searchLayer,
            )
        }
        overlay()
    }
}

@Composable
private fun StableStoryLayer(
    active: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.then(if (active) Modifier else Modifier.clearAndSetSemantics { }),
    ) {
        content()
        if (!active) {
            Box(
                Modifier
                    .matchParentSize()
                    .consumeAllPointerGestures(),
            )
        }
    }
}
