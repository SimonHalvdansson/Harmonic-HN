package com.simon.harmonichackernews.ui.navigation

import androidx.navigationevent.NavigationEvent

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.setOutline
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * Default Android cross-activity predictive-back motion.
 *
 * Gesture progress uses AOSP's BackGestureInterpolator, while the motion and geometry follow
 * Decompose's Android V2 predictive-back implementation:
 * https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/animation/BackGestureInterpolator.java
 * https://github.com/arkivanov/Decompose/blob/master/extensions-compose/src/commonMain/kotlin/com/arkivanov/decompose/extensions/compose/stack/animation/predictiveback/AndroidPredictiveBackAnimatableV2.kt
 */
class DefaultActivityPredictiveBackAnimation(
    private val initialEvent: NavigationEvent,
) {
    private val finishProgressAnimatable = Animatable(initialValue = 0f)
    private val progressAnimatable = Animatable(
        initialValue = BackGestureEasing.transform(initialEvent.progress),
    )
    private var edge by mutableIntStateOf(initialEvent.swipeEdge)
    private var touchY by mutableFloatStateOf(initialEvent.touchY)

    val exitModifier: Modifier
        get() = Modifier.composed {
            withLayoutCorners { corners -> predictiveBackSurface(corners, entering = false) }
        }

    val enterModifier: Modifier
        get() = Modifier
            .drawWithContent {
                drawContent()
                drawRect(
                    color = Color.Black.copy(
                        alpha = (1f - finishProgressAnimatable.value) * RevealedContentScrimAlpha,
                    ),
                )
            }
            .composed {
                withLayoutCorners { corners -> predictiveBackSurface(corners, entering = true) }
            }

    /** Read gesture state and measured size only when drawing the retained page surface. */
    @Composable
    private fun Modifier.predictiveBackSurface(corners: LayoutCorners, entering: Boolean): Modifier {
        val surface = rememberGraphicsLayer()
        return drawWithContent {
            val finishProgress = finishProgressAnimatable.value
            val progress = progressAnimatable.value
            val scale = scaleFactor()
            val shapeProgress = if (entering) lerp(progress, 0f, finishProgress) else progress
            // Keep Haze's source/effect coordinates at rest while moving the complete page.
            surface.record { this@drawWithContent.drawContent() }
            surface.scaleX = scale
            surface.scaleY = scale
            surface.translationX = if (entering) {
                lerp(-size.width * FinishTranslationFraction, 0f, finishProgress)
            } else exitOffsetX(size.width)
            surface.translationY = exitOffsetY(size.height)
            surface.alpha = if (entering) 1f else 1f - finishProgress
            surface.setOutline(corners.toShape(shapeProgress).createOutline(size, layoutDirection, this))
            surface.clip = true
            surface.compositingStrategy = CompositingStrategy.Offscreen
            drawLayer(surface)
        }
    }

    private fun Density.exitOffsetX(width: Float): Float {
        if (width == 0f) return 0f

        val initialOffsetX = when (edge) {
            NavigationEvent.EDGE_LEFT ->
                (width - width * initialScaleFactor()) / 2f -
                    GestureEdgeInset.toPx() * progressAnimatable.value

            NavigationEvent.EDGE_RIGHT,
            NavigationEvent.EDGE_NONE,
            -> 0f

            else -> 0f
        }

        return lerp(
            start = initialOffsetX,
            stop = width * FinishTranslationFraction,
            fraction = finishProgressAnimatable.value,
        )
    }

    private fun initialScaleFactor(): Float = lerp(
        start = 1f,
        stop = GestureTargetScale,
        fraction = progressAnimatable.value,
    )

    private fun scaleFactor(): Float = lerp(
        start = initialScaleFactor(),
        stop = 1f,
        fraction = finishProgressAnimatable.value,
    )

    private fun Density.exitOffsetY(height: Float): Float {
        if (height == 0f) return 0f

        val translationYLimit = height / 20f - GestureEdgeInset.toPx()
        val translationYFactor =
            ((touchY - initialEvent.touchY) / height) *
                (progressAnimatable.value * 3f).coerceAtMost(1f)

        return lerp(
            start = translationYLimit * translationYFactor,
            stop = 0f,
            fraction = finishProgressAnimatable.value,
        )
    }

    suspend fun animate(event: NavigationEvent) {
        edge = event.swipeEdge
        touchY = event.touchY
        progressAnimatable.animateTo(BackGestureEasing.transform(event.progress))
    }

    suspend fun finish() {
        val velocityFactor = progressAnimatable.velocity.coerceAtMost(1f)
        val progress = progressAnimatable.value

        coroutineScope {
            joinAll(
                launch {
                    progressAnimatable.animateTo(
                        progress + (1f - progress) * velocityFactor,
                    )
                },
                launch { finishProgressAnimatable.animateTo(targetValue = 1f) },
            )
        }
    }

    suspend fun cancel() {
        progressAnimatable.animateTo(0f)
    }

    private companion object {
        const val GestureTargetScale = 0.85f
        const val FinishTranslationFraction = 0.2f
        const val RevealedContentScrimAlpha = 0.25f
        val GestureEdgeInset = 8.dp
        val BackGestureEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
    }
}

internal data class LayoutCorners(
    val topStart: LayoutCorner = LayoutCorner(),
    val topEnd: LayoutCorner = LayoutCorner(),
    val bottomEnd: LayoutCorner = LayoutCorner(),
    val bottomStart: LayoutCorner = LayoutCorner(),
)

internal data class LayoutCorner(
    val radius: Dp = 16.dp,
    val isFixed: Boolean = false,
)

private fun LayoutCorners.toShape(progress: Float): RoundedCornerShape = RoundedCornerShape(
    topStart = topStart.progressRadius(progress),
    topEnd = topEnd.progressRadius(progress),
    bottomEnd = bottomEnd.progressRadius(progress),
    bottomStart = bottomStart.progressRadius(progress),
)

private fun LayoutCorner.progressRadius(progress: Float): Dp =
    if (isFixed) radius else radius * progress

@Composable
internal expect fun Modifier.withLayoutCorners(
    block: @Composable Modifier.(LayoutCorners) -> Modifier,
): Modifier
