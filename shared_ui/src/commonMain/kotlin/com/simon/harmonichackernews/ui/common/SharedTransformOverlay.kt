package com.simon.harmonichackernews.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Shared container-transform shell for modal previews and action cards.
 *
 * Callers supply platform-specific content and back callbacks; dimming, safe-area placement,
 * source-to-card transforms, predictive-back transforms, and input isolation remain portable.
 */
@Composable
fun SharedTransformOverlay(
    contentKey: Any,
    sourceBounds: Rect?,
    dismissRequestVersion: Int,
    predictiveBackProgress: Float,
    predictiveBackEdge: Int,
    maxWidth: Dp,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    shape: Shape,
    containerColor: Color,
    shadowElevation: Dp = 8.dp,
    keepContentOpaqueWithSource: Boolean = false,
    consumeAllGestures: Boolean = true,
    onDismissRequest: () -> Unit,
    onDismissAnimationFinished: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val transformProgress = remember(contentKey) { Animatable(0f) }
    var targetBounds by remember(contentKey) { mutableStateOf<Rect?>(null) }

    LaunchedEffect(contentKey, targetBounds) {
        if (targetBounds != null && dismissRequestVersion == 0) {
            transformProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            )
        }
    }
    LaunchedEffect(dismissRequestVersion) {
        if (dismissRequestVersion <= 0) return@LaunchedEffect
        transformProgress.animateTo(
            targetValue = 0f,
            animationSpec = tween(280, easing = FastOutSlowInEasing),
        )
        onDismissAnimationFinished()
    }

    val progress = transformProgress.value
    val predictiveEased = predictiveBackProgress.coerceIn(0f, 1f).let {
        1f - (1f - it) * (1f - it)
    }
    val target = targetBounds
    val source = sourceBounds
    val startScaleX = if (target != null && source != null && target.width > 0f) {
        (source.width / target.width).coerceIn(0.08f, 1.15f)
    } else {
        0.96f
    }
    val startScaleY = if (target != null && source != null && target.height > 0f) {
        (source.height / target.height).coerceIn(0.08f, 1.15f)
    } else {
        0.96f
    }
    val startTranslationX = if (target != null && source != null) {
        source.center.x - target.center.x
    } else {
        0f
    }
    val startTranslationY = if (target != null && source != null) {
        source.center.y - target.center.y
    } else {
        0f
    }
    val backDirection = if (predictiveBackEdge == 1) -1f else 1f
    val backTranslationX = with(density) { 56.dp.toPx() } * predictiveEased * backDirection
    val backTranslationY = with(density) { 18.dp.toPx() } * predictiveEased
    val gestureBlocker = if (consumeAllGestures) Modifier.consumeModalGestures() else Modifier

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha = 0.32f * progress * (1f - 0.55f * predictiveEased),
                    ),
                )
                .then(gestureBlocker)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .onGloballyPositioned { targetBounds = it.boundsInWindow() }
                    .graphicsLayer {
                        val sharedScaleX = startScaleX + (1f - startScaleX) * progress
                        val sharedScaleY = startScaleY + (1f - startScaleY) * progress
                        val backScale = 1f - 0.1f * predictiveEased
                        scaleX = sharedScaleX * backScale
                        scaleY = sharedScaleY * backScale
                        translationX = startTranslationX * (1f - progress) + backTranslationX
                        translationY = startTranslationY * (1f - progress) + backTranslationY
                        alpha = when {
                            source == null -> progress
                            keepContentOpaqueWithSource -> 1f
                            else -> max(0.7f, progress)
                        }
                        transformOrigin = TransformOrigin(
                            pivotFractionX = if (backDirection > 0f) 0f else 1f,
                            pivotFractionY = 0.5f,
                        )
                    }
                    .then(gestureBlocker)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = shape,
                color = containerColor,
                shadowElevation = shadowElevation,
            ) {
                content()
            }
        }
    }
}

private fun Modifier.consumeModalGestures(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
