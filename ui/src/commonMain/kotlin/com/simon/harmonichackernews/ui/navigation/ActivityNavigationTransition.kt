package com.simon.harmonichackernews.ui.navigation

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal val ActivityNavigationTransitionOffset = 96.dp
internal const val ActivityNavigationTransitionDurationMillis = 450
internal const val ActivityNavigationFadeDurationMillis = 83
internal const val ActivityNavigationOpenFadeDelayMillis = 50
internal const val ActivityNavigationCloseFadeDelayMillis = 35

/**
 * Recreates the platform activity surface used by Android's default open/close animations.
 *
 * Android fills the gap left by the translated destination with a stretched sample of the window's
 * edge. The destination is recorded once per transition frame for edge sampling, its edge is
 * stretched into the exposed gap, and the destination itself is drawn translated. A parent layer
 * then applies alpha once to the combined result, matching the platform surface composition.
 */
@Composable
internal fun ActivityNavigationTransitionViewport(
    transition: Transition<EnterExitState>,
    transitionOffsetPx: Int,
    baseTranslationX: Float = 0f,
    skipExitAnimation: Boolean = false,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val transitionTranslationX by transition.animateFloat(
        transitionSpec = {
            if (targetState != EnterExitState.Visible && skipExitAnimation) {
                snap()
            } else {
                tween(
                    durationMillis = ActivityNavigationTransitionDurationMillis,
                    easing = activityNavigationEasing(),
                )
            }
        },
        label = "activity navigation translation",
    ) { state ->
        if (state == EnterExitState.Visible) 0f else transitionOffsetPx.toFloat()
    }
    val transitionAlpha by transition.animateFloat(
        transitionSpec = {
            if (targetState != EnterExitState.Visible && skipExitAnimation) {
                snap()
            } else {
                tween(
                    durationMillis = ActivityNavigationFadeDurationMillis,
                    delayMillis = if (targetState == EnterExitState.Visible) {
                        ActivityNavigationOpenFadeDelayMillis
                    } else {
                        ActivityNavigationCloseFadeDelayMillis
                    },
                    easing = LinearEasing,
                )
            }
        },
        label = "activity navigation alpha",
    ) { state ->
        if (state == EnterExitState.Visible) 1f else 0f
    }

    ActivityNavigationViewportLayout(
        translationX = baseTranslationX + transitionTranslationX,
        alpha = transitionAlpha,
        modifier = modifier,
        contentModifier = contentModifier,
        content = content,
    )
}

@Composable
private fun ActivityNavigationViewportLayout(
    translationX: Float,
    alpha: Float,
    modifier: Modifier,
    contentModifier: Modifier,
    content: @Composable () -> Unit,
) {
    val destinationLayer = rememberGraphicsLayer()
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent destination@{
                    if (abs(translationX) < 0.01f) {
                        drawContent()
                        return@destination
                    }

                    destinationLayer.record { this@destination.drawContent() }
                    withTransform({ translate(left = translationX) }) {
                        this@destination.drawContent()
                    }
                    val extensionWidth = abs(translationX).coerceAtMost(size.width)
                    if (translationX > 0f) {
                        drawActivityNavigationEdge(
                            layer = destinationLayer,
                            extensionLeft = 0f,
                            extensionWidth = extensionWidth,
                            sourceLeft = ActivityNavigationEdgeSampleX,
                        )
                    } else {
                        val extensionLeft = size.width - extensionWidth
                        drawActivityNavigationEdge(
                            layer = destinationLayer,
                            extensionLeft = extensionLeft,
                            extensionWidth = extensionWidth,
                            sourceLeft = size.width - ActivityNavigationEdgeSampleX - 1f,
                        )
                    }
                }
                .then(contentModifier),
        ) {
            content()
        }
    }
}

private fun DrawScope.drawActivityNavigationEdge(
    layer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    extensionLeft: Float,
    extensionWidth: Float,
    sourceLeft: Float,
) {
    val extensionRight = extensionLeft + extensionWidth
    val edgeTransform = Matrix().apply {
        this[0, 0] = extensionWidth.coerceAtLeast(1f)
        this[3, 0] = extensionLeft - this[0, 0] * sourceLeft
    }
    clipRect(left = extensionLeft, right = extensionRight) {
        withTransform({ transform(edgeTransform) }) {
            drawLayer(layer)
        }
    }
}

// Sample inside the texture so filtering never mixes the edge with transparent padding.
private const val ActivityNavigationEdgeSampleX = 4.5f

internal fun activityNavigationEasing(): Easing = PathEasing(
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    },
)
