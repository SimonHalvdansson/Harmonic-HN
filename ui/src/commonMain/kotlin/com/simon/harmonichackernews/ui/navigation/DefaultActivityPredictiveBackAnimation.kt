package com.simon.harmonichackernews.ui.navigation

import androidx.navigationevent.NavigationEvent

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.setOutline
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
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
            withLayoutCorners { corners ->
                exitModifier(corners.toShape(progressAnimatable.value))
            }
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
                val shapeProgress = lerp(
                    start = progressAnimatable.value,
                    stop = 0f,
                    fraction = finishProgressAnimatable.value,
                )
                withLayoutCorners { corners ->
                    enterModifier(corners.toShape(shapeProgress))
                }
            }

    @Composable
    private fun Modifier.enterModifier(shape: Shape): Modifier {
        var size by remember { mutableStateOf(Size.Zero) }
        val scaleFactor = scaleFactor()
        val density = LocalDensity.current

        return this
            .onPlaced { size = it.size.toSize() }
            .predictiveBackSurface(
                scaleX = scaleFactor,
                scaleY = scaleFactor,
                translationX = lerp(
                    start = -size.width * FinishTranslationFraction,
                    stop = 0f,
                    fraction = finishProgressAnimatable.value,
                ),
                translationY = density.exitOffsetY(height = size.height),
                shape = shape,
            )
    }

    @Composable
    private fun Modifier.exitModifier(shape: Shape): Modifier {
        var size by remember { mutableStateOf(Size.Zero) }
        val scaleFactor = scaleFactor()
        val density = LocalDensity.current

        return this
            .onPlaced { size = it.size.toSize() }
            .predictiveBackSurface(
                scaleX = scaleFactor,
                scaleY = scaleFactor,
                alpha = 1f - finishProgressAnimatable.value,
                translationX = density.exitOffsetX(width = size.width),
                translationY = density.exitOffsetY(height = size.height),
                shape = shape,
            )
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

/** Move the rendered surface without changing the coordinates used by its backdrop effects. */
@Composable
private fun Modifier.predictiveBackSurface(
    scaleX: Float,
    scaleY: Float,
    translationX: Float,
    translationY: Float,
    shape: Shape,
    alpha: Float = 1f,
): Modifier {
    val surface = rememberGraphicsLayer()
    return drawWithContent {
        // A layout graphicsLayer also transforms Haze's window-space source/effect positions.
        // Record the complete page in its own coordinate space before moving it, as we do for
        // forward activity navigation, so glass and blur remain part of the same stable surface.
        surface.record { this@drawWithContent.drawContent() }
        surface.scaleX = scaleX
        surface.scaleY = scaleY
        surface.translationX = translationX
        surface.translationY = translationY
        surface.alpha = alpha
        surface.setOutline(shape.createOutline(size, layoutDirection, this))
        surface.clip = true
        surface.compositingStrategy = CompositingStrategy.Offscreen
        drawLayer(surface)
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
