package com.simon.harmonichackernews.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Shared motion for replacing content while the surrounding list/detail panes remain in place. */
internal fun paneDetailSwitchTransition(): ContentTransform = ContentTransform(
    targetContentEnter = scaleIn(
        tween(PaneDetailTransitionDurationMillis, easing = paneNavigationEasing()),
        initialScale = 0.85f,
    ) + fadeIn(
        tween(PaneDetailAlphaDurationMillis, PaneDetailAlphaDelayMillis, LinearEasing),
    ),
    initialContentExit = scaleOut(
        tween(PaneDetailTransitionDurationMillis, easing = paneNavigationEasing()),
        targetScale = 1.15f,
    ) + fadeOut(
        tween(PaneDetailAlphaDurationMillis, PaneDetailAlphaDelayMillis, LinearEasing),
    ),
    targetContentZIndex = 1f,
)

/**
 * Runs the detail-pane entrance without retaining the outgoing composition. This is important for
 * platform views such as desktop WebView2, which cannot safely remain mounted twice while a story
 * destination is replaced.
 */
@Composable
internal fun PaneDetailSwitchIn(
    contentKey: Any,
    animate: Boolean,
    initialScale: Float = 0.85f,
    content: @Composable () -> Unit,
) {
    key(contentKey) {
        val scale = remember { Animatable(if (animate) initialScale else 1f) }
        val alpha = remember { Animatable(if (animate) 0f else 1f) }

        LaunchedEffect(Unit) {
            if (animate) {
                coroutineScope {
                    launch {
                        scale.animateTo(
                            1f,
                            tween(
                                PaneDetailTransitionDurationMillis,
                                easing = paneNavigationEasing(),
                            ),
                        )
                    }
                    launch {
                        delay(PaneDetailAlphaDelayMillis.toLong())
                        alpha.animateTo(
                            1f,
                            tween(PaneDetailAlphaDurationMillis, easing = LinearEasing),
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                },
        ) {
            content()
        }
    }
}

private fun paneNavigationEasing() = PathEasing(
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    },
)

private const val PaneDetailTransitionDurationMillis = 300
private const val PaneDetailAlphaDelayMillis = 50
private const val PaneDetailAlphaDurationMillis = 50
